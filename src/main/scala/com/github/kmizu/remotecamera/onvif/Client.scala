package com.github.kmizu.remotecamera.onvif

import cats.effect.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.typelevel.ci.CIString
import scala.xml.*
import java.security.{MessageDigest, SecureRandom}
import java.util.Base64
import java.time.Instant
import com.github.kmizu.remotecamera.CameraConfig

// ===========================================================================
// ONVIF session state (immutable, created once during connect)
// ===========================================================================

case class OnvifSession(
    mediaUrl: String,
    ptzUrl: String,
    profileToken: String,
    snapshotUri: Option[String],
    streamUri: Option[String],
)

// ===========================================================================
// ONVIF client — SOAP over HTTP with WS-Security UsernameToken
// ===========================================================================

class OnvifClient(httpClient: Client[IO], config: CameraConfig):

  private val deviceUrl =
    s"http://${config.host}:${config.onvifPort}/onvif/device_service"

  // -- Public API -----------------------------------------------------------

  /** Connect: discover services, profile, snapshot/stream URIs. */
  def connect: IO[OnvifSession] =
    for
      caps     <- getCapabilities
      _        <- IO.println(s"Capabilities: $caps")
      mediaUrl  = caps.getOrElse("media", deviceUrl)
      ptzUrl    = caps.getOrElse("ptz", deviceUrl)
      _        <- IO.println(s"Media URL: $mediaUrl, PTZ URL: $ptzUrl")
      token    <- getProfileToken(mediaUrl)
      _        <- IO.println(s"Profile token: $token")
      snap     <- getSnapshotUri(mediaUrl, token).handleErrorWith: e =>
                    IO.println(s"GetSnapshotUri not supported: ${e.getMessage}") >>
                    IO.pure(None)
      stream   <- getStreamUri(mediaUrl, token).handleErrorWith: e =>
                    IO.println(s"GetStreamUri failed: ${e.getMessage}") >>
                    IO.pure(None)
      _        <- IO.println(s"Snapshot URI: ${snap.getOrElse("none")}")
      _        <- IO.println(s"Stream URI: ${stream.getOrElse("none")}")
    yield OnvifSession(mediaUrl, ptzUrl, token, snap, stream)

  /** Get device information (manufacturer, model, firmware, etc.). */
  def getDeviceInfo: IO[Map[String, String]] =
    val body = <GetDeviceInformation xmlns="http://www.onvif.org/ver10/device/wsdl"/>
    soapCall(deviceUrl, body).map: resp =>
      val info = resp \\ "GetDeviceInformationResponse"
      List("Manufacturer", "Model", "FirmwareVersion", "SerialNumber", "HardwareId")
        .flatMap(f => (info \\ f).headOption.map(n => f -> n.text.trim))
        .toMap

  /** Relative PTZ move. pan/tilt are normalized [-1.0, 1.0]. */
  def relativeMove(session: OnvifSession, pan: Double, tilt: Double): IO[Unit] =
    val body =
      <RelativeMove xmlns="http://www.onvif.org/ver20/ptz/wsdl">
        <ProfileToken>{session.profileToken}</ProfileToken>
        <Translation>
          <PanTilt xmlns="http://www.onvif.org/ver10/schema" x={pan.toString} y={tilt.toString}/>
        </Translation>
      </RelativeMove>
    soapCall(session.ptzUrl, body).void

  /** Get current PTZ position (normalized). */
  def getStatus(session: OnvifSession): IO[Option[(Double, Double)]] =
    val body =
      <GetStatus xmlns="http://www.onvif.org/ver20/ptz/wsdl">
        <ProfileToken>{session.profileToken}</ProfileToken>
      </GetStatus>
    soapCall(session.ptzUrl, body).map: resp =>
      val pt = resp \\ "PanTilt"
      for
        node <- pt.headOption
        x    <- node.attribute("x").flatMap(_.headOption).map(_.text.toDouble)
        y    <- node.attribute("y").flatMap(_.headOption).map(_.text.toDouble)
      yield (x, y)

  /** List PTZ presets. Returns list of (token, name). */
  def getPresets(session: OnvifSession): IO[List[(String, String)]] =
    val body =
      <GetPresets xmlns="http://www.onvif.org/ver20/ptz/wsdl">
        <ProfileToken>{session.profileToken}</ProfileToken>
      </GetPresets>
    soapCall(session.ptzUrl, body).map: resp =>
      (resp \\ "Preset").toList.flatMap: node =>
        node.attribute("token").flatMap(_.headOption).map: tok =>
          val name = (node \\ "Name").headOption.map(_.text.trim).getOrElse(tok.text)
          (tok.text, name)

  /** Go to a saved preset position. */
  def gotoPreset(session: OnvifSession, presetToken: String): IO[Unit] =
    val body =
      <GotoPreset xmlns="http://www.onvif.org/ver20/ptz/wsdl">
        <ProfileToken>{session.profileToken}</ProfileToken>
        <PresetToken>{presetToken}</PresetToken>
      </GotoPreset>
    soapCall(session.ptzUrl, body).void

  /** Download snapshot image as raw bytes (using Basic Auth). */
  def downloadSnapshot(uri: String): IO[Array[Byte]] =
    val req = Request[IO](uri = Uri.unsafeFromString(uri))
      .putHeaders(Authorization(BasicCredentials(config.username, config.password)))
    httpClient.expect[Array[Byte]](req)

  // -- SOAP transport -------------------------------------------------------

  private def soapCall(url: String, body: Elem): IO[Elem] =
    val envelope = buildEnvelope(body)
    val request = Request[IO](
      method = Method.POST,
      uri = Uri.unsafeFromString(url),
    ).withEntity(envelope)
      .putHeaders(
        Header.Raw(CIString("Content-Type"), "application/soap+xml; charset=utf-8")
      )
    httpClient.expect[String](request).flatMap: text =>
      IO.fromTry(scala.util.Try(XML.loadString(text)))

  private def buildEnvelope(body: Elem): String =
    val sec = WsSecurity.header(config.username, config.password)
    val envelope =
      <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
        <s:Header>{sec}</s:Header>
        <s:Body>{body}</s:Body>
      </s:Envelope>
    """<?xml version="1.0" encoding="utf-8"?>""" + envelope.toString

  // -- Discovery helpers ----------------------------------------------------

  private def getCapabilities: IO[Map[String, String]] =
    val body =
      <GetCapabilities xmlns="http://www.onvif.org/ver10/device/wsdl">
        <Category>All</Category>
      </GetCapabilities>
    soapCall(deviceUrl, body).map: resp =>
      val caps = resp \\ "Capabilities"
      val entries = for
        svc <- List("Media", "PTZ")
        node <- (caps \\ svc).headOption
        addr <- (node \\ "XAddr").headOption
      yield svc.toLowerCase -> addr.text.trim
      entries.toMap

  private def getProfileToken(mediaUrl: String): IO[String] =
    val body = <GetProfiles xmlns="http://www.onvif.org/ver10/media/wsdl"/>
    soapCall(mediaUrl, body).flatMap: resp =>
      val tokens = (resp \\ "Profiles").flatMap(_.attribute("token")).flatten.map(_.text)
      IO.fromOption(tokens.headOption)(
        RuntimeException("No ONVIF media profiles found")
      )

  private def getSnapshotUri(mediaUrl: String, token: String): IO[Option[String]] =
    val body =
      <GetSnapshotUri xmlns="http://www.onvif.org/ver10/media/wsdl">
        <ProfileToken>{token}</ProfileToken>
      </GetSnapshotUri>
    soapCall(mediaUrl, body).map: resp =>
      (resp \\ "Uri").headOption.map(_.text.trim)

  private def getStreamUri(mediaUrl: String, token: String): IO[Option[String]] =
    val body =
      <GetStreamUri xmlns="http://www.onvif.org/ver10/media/wsdl">
        <StreamSetup>
          <Stream xmlns="http://www.onvif.org/ver10/schema">RTP-Unicast</Stream>
          <Transport xmlns="http://www.onvif.org/ver10/schema">
            <Protocol>RTSP</Protocol>
          </Transport>
        </StreamSetup>
        <ProfileToken>{token}</ProfileToken>
      </GetStreamUri>
    soapCall(mediaUrl, body).map: resp =>
      (resp \\ "Uri").headOption.map(_.text.trim)

// ===========================================================================
// WS-Security UsernameToken (Password Digest)
// ===========================================================================

private object WsSecurity:
  private val WsseNs =
    "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
  private val WsuNs =
    "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
  private val PasswordType =
    "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest"
  private val EncodingType =
    "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary"
  private val rng = new SecureRandom()

  def header(username: String, password: String): Elem =
    val nonce = new Array[Byte](16)
    rng.nextBytes(nonce)
    val created = Instant.now().toString
    val digest =
      val md = MessageDigest.getInstance("SHA-1")
      md.update(nonce)
      md.update(created.getBytes("UTF-8"))
      md.update(password.getBytes("UTF-8"))
      Base64.getEncoder.encodeToString(md.digest())
    val nonceB64 = Base64.getEncoder.encodeToString(nonce)

    <wsse:Security xmlns:wsse={WsseNs}>
      <wsse:UsernameToken>
        <wsse:Username>{username}</wsse:Username>
        <wsse:Password Type={PasswordType}>{digest}</wsse:Password>
        <wsse:Nonce EncodingType={EncodingType}>{nonceB64}</wsse:Nonce>
        <wsu:Created xmlns:wsu={WsuNs}>{created}</wsu:Created>
      </wsse:UsernameToken>
    </wsse:Security>

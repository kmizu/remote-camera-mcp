package com.github.kmizu.remotecamera

import cats.effect.*
import cats.syntax.all.*
import io.circe.*
import scala.concurrent.duration.DurationInt
import com.github.kmizu.remotecamera.mcp.*
import com.github.kmizu.remotecamera.onvif.{OnvifClient, OnvifSession}
import java.nio.file.{Files, Path}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

// ===========================================================================
// Camera position tracking
// ===========================================================================

case class Position(pan: Double = 0.0, tilt: Double = 0.0)

// ===========================================================================
// Camera — wraps ONVIF + ffmpeg for capture / PTZ / audio
// ===========================================================================

class Camera(
    config: CameraConfig,
    serverConfig: ServerConfig,
    onvif: OnvifClient,
    sessionRef: Ref[IO, OnvifSession],
    posRef: Ref[IO, Position],
):

  private val captureDir = Path.of(serverConfig.captureDir)
  private val PanRange   = 180.0
  private val TiltRange  = 90.0
  private val ts         = DateTimeFormatter.ofPattern("yyyyMMdd_HHMMss")

  // -- Tool dispatch --------------------------------------------------------

  def handleTool(name: String, args: Json): IO[CallToolResult] =
    name match
      case "see"                 => see
      case "look_left"           => move("left", args.hcursor.get[Int]("degrees").getOrElse(30))
      case "look_right"          => move("right", args.hcursor.get[Int]("degrees").getOrElse(30))
      case "look_up"             => move("up", args.hcursor.get[Int]("degrees").getOrElse(20))
      case "look_down"           => move("down", args.hcursor.get[Int]("degrees").getOrElse(20))
      case "look_around"         => lookAround
      case "listen"              => listen(args)
      case "camera_info"         => cameraInfo
      case "camera_presets"      => presets
      case "camera_go_to_preset" => gotoPreset(args.hcursor.get[String]("preset_id").getOrElse(""))
      case other => IO.pure(CallToolResult(List(TextContent(s"Unknown tool: $other")), isError = Some(true)))

  // -- Capture --------------------------------------------------------------

  private def see: IO[CallToolResult] =
    captureImage.map: (b64, w, h, path) =>
      CallToolResult(List(
        ImageContent(b64, "image/jpeg"),
        TextContent(s"Captured ${w}x$h image at ${path.getFileName}"),
      ))

  private def captureImage: IO[(String, Int, Int, Path)] =
    val now  = LocalDateTime.now().format(ts)
    val file = captureDir.resolve(s"capture_$now.jpg")
    IO(Files.createDirectories(captureDir)) >>
      captureViaOnvif(file)
        .handleErrorWith(_ => captureViaFfmpeg(file))
        .flatMap(_ => encodeImage(file))

  private def captureViaOnvif(out: Path): IO[Unit] =
    for
      session <- sessionRef.get
      uri     <- IO.fromOption(session.snapshotUri)(RuntimeException("No snapshot URI"))
      bytes   <- onvif.downloadSnapshot(uri)
      _       <- IO(Files.write(out, bytes))
    yield ()

  private def captureViaFfmpeg(out: Path): IO[Unit] =
    val url = rtspUrl
    ffmpeg(List(
      "-y", "-rtsp_transport", "tcp",
      "-i", url,
      "-frames:v", "1", "-f", "image2",
      out.toString,
    ))

  private def encodeImage(path: Path): IO[(String, Int, Int, Path)] = IO:
    val bytes = Files.readAllBytes(path)
    val b64   = Base64.getEncoder.encodeToString(bytes)
    // Quick JPEG dimension read (SOF0 marker)
    val (w, h) = jpegDimensions(bytes)
    (b64, w, h, path)

  private def jpegDimensions(data: Array[Byte]): (Int, Int) =
    // Scan for SOF0 (0xFF 0xC0) marker
    var i = 0
    while i < data.length - 9 do
      if data(i) == 0xFF.toByte && (data(i + 1) == 0xC0.toByte || data(i + 1) == 0xC2.toByte) then
        val h = ((data(i + 5) & 0xFF) << 8) | (data(i + 6) & 0xFF)
        val w = ((data(i + 7) & 0xFF) << 8) | (data(i + 8) & 0xFF)
        return (w, h)
      i += 1
    (0, 0)

  // -- PTZ ------------------------------------------------------------------

  private def move(direction: String, degrees: Int): IO[CallToolResult] =
    val deg = degrees.max(1).min(90)
    val (panDeg, tiltDeg) = direction match
      case "left"  => (deg.toDouble, 0.0)
      case "right" => (-deg.toDouble, 0.0)
      // Tapo: y- = physical UP, y+ = physical DOWN
      case "up"    => (0.0, -deg.toDouble)
      case "down"  => (0.0, deg.toDouble)
      case _       => (0.0, 0.0)

    val panNorm  = (panDeg / PanRange).max(-1.0).min(1.0)
    val tiltNorm = (tiltDeg / TiltRange).max(-1.0).min(1.0)

    val (finalPan, finalTilt) =
      if config.mountMode == "ceiling" then (-panNorm, tiltNorm) else (panNorm, tiltNorm)

    for
      session <- sessionRef.get
      _       <- onvif.relativeMove(session, finalPan, finalTilt)
      _       <- posRef.update: pos =>
                   direction match
                     case "left"  => pos.copy(pan = (pos.pan - deg).max(-180))
                     case "right" => pos.copy(pan = (pos.pan + deg).min(180))
                     case "up"    => pos.copy(tilt = (pos.tilt + deg).min(90))
                     case "down"  => pos.copy(tilt = (pos.tilt - deg).max(-90))
                     case _       => pos
      _       <- IO.sleep(500.millis)
    yield CallToolResult(List(TextContent(s"Moved $direction by $deg degrees")))

  private def lookAround: IO[CallToolResult] =
    for
      center <- captureImage
      _      <- move("left", 45)
      _      <- IO.sleep(300.millis)
      left   <- captureImage
      _      <- move("right", 90)
      _      <- IO.sleep(300.millis)
      right  <- captureImage
      _      <- move("left", 45)
      _      <- move("up", 20)
      _      <- IO.sleep(300.millis)
      up     <- captureImage
      _      <- move("down", 20)
    yield
      val images = List("Center" -> center, "Left" -> left, "Right" -> right, "Up" -> up)
      val content = images.flatMap: (label, img) =>
        val (b64, w, h, _) = img
        List(
          TextContent(s"--- $label View ---"),
          ImageContent(b64, "image/jpeg"),
        )
      CallToolResult(content :+ TextContent(s"Captured ${images.size} angles. Returned to center."))

  // -- Audio ----------------------------------------------------------------

  private def listen(args: Json): IO[CallToolResult] =
    val duration   = args.hcursor.get[Int]("duration").getOrElse(5).min(30).max(1)
    val transcribe = args.hcursor.get[Boolean]("transcribe").getOrElse(true)
    val now        = LocalDateTime.now().format(ts)
    val wavFile    = captureDir.resolve(s"audio_$now.wav")

    val input = if serverConfig.micSource == "local" then
      List("-f", "pulse", "-i", "default")
    else
      List("-rtsp_transport", "tcp", "-i", rtspUrl)

    for
      _ <- IO(Files.createDirectories(captureDir))
      _ <- ffmpeg(
             List("-y") ++ input ++
               List("-t", duration.toString, "-vn", "-acodec", "pcm_s16le", wavFile.toString)
           )
      transcript <- if transcribe then whisperTranscribe(wavFile) else IO.pure(Option.empty[String])
    yield
      val text = new StringBuilder
      text ++= s"Recorded ${duration}s of audio\nFile: $wavFile\n"
      transcript.foreach(t => text ++= s"\n--- Transcript ---\n$t")
      CallToolResult(List(TextContent(text.toString)))

  private def whisperTranscribe(audioPath: Path): IO[Option[String]] =
    IO.blocking:
      import scala.sys.process.*
      val outDir = audioPath.getParent.toString
      val exitCode = List(
        "whisper", audioPath.toString,
        "--model", "base",
        "--output_format", "txt",
        "--output_dir", outDir,
      ).!(ProcessLogger(_ => (), _ => ()))

      if exitCode == 0 then
        val txtFile = Path.of(outDir, audioPath.getFileName.toString.replaceAll("\\.[^.]+$", ".txt"))
        if Files.exists(txtFile) then Some(Files.readString(txtFile).trim)
        else None
      else None
    .handleError(_ => None)

  // -- Info -----------------------------------------------------------------

  private def cameraInfo: IO[CallToolResult] =
    onvif.getDeviceInfo.map: info =>
      val text = info.map((k, v) => s"$k: $v").mkString("\n")
      CallToolResult(List(TextContent(s"Camera Info:\n$text")))

  private def presets: IO[CallToolResult] =
    sessionRef.get.flatMap: session =>
      onvif.getPresets(session).map: ps =>
        val text = if ps.isEmpty then "No presets configured"
        else ps.map((tok, name) => s"  $tok: $name").mkString("Presets:\n", "\n", "")
        CallToolResult(List(TextContent(text)))

  private def gotoPreset(id: String): IO[CallToolResult] =
    sessionRef.get.flatMap: session =>
      onvif.gotoPreset(session, id).as(
        CallToolResult(List(TextContent(s"Moved to preset $id")))
      )

  // -- Helpers --------------------------------------------------------------

  private def rtspUrl: String =
    config.streamUrl.getOrElse(
      s"rtsp://${config.username}:${config.password}@${config.host}:554/stream1"
    )

  private def ffmpeg(args: List[String]): IO[Unit] = IO.blocking:
    import scala.sys.process.*
    val exitCode = ("ffmpeg" :: args).!(ProcessLogger(_ => (), _ => ()))
    if exitCode != 0 then throw RuntimeException(s"ffmpeg exited with code $exitCode")

object Camera:
  /** Create a connected camera. */
  def create(
      config: CameraConfig,
      serverConfig: ServerConfig,
      onvif: OnvifClient,
  ): IO[Camera] =
    for
      session    <- onvif.connect
      sessionRef <- Ref.of[IO, OnvifSession](session)
      posRef     <- Ref.of[IO, Position](Position())
    yield Camera(config, serverConfig, onvif, sessionRef, posRef)

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.jetbrains.signatureverifier.ILogger
import com.jetbrains.signatureverifier.InvalidDataException
import com.jetbrains.signatureverifier.crypt.SignatureVerificationParams
import com.jetbrains.signatureverifier.crypt.SignedMessage
import com.jetbrains.signatureverifier.crypt.SignedMessageVerifier
import com.jetbrains.signatureverifier.crypt.VerifySignatureStatus
import com.jetbrains.signatureverifier.macho.MachoArch
import com.jetbrains.util.filetype.FileProperties
import com.jetbrains.util.filetype.FileType
import com.jetbrains.util.filetype.FileTypeDetector.DetectFileType
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.PackageIndexBuilder
import org.jetbrains.intellij.build.io.WRITE_OPEN_OPTION
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.io.suspendAwareReadZipFile
import org.jetbrains.intellij.build.io.writeToFileChannelFully
import org.jetbrains.intellij.build.io.writeZipUsingTempFile
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.relativeTo

internal fun isMacLibrary(name: String): Boolean =
  name.endsWith(".jnilib") || name.endsWith(".dylib") || name.endsWith(".so") || name.endsWith(".tbd")

internal fun recursivelySignMacBinaries(
  coroutineScope: CoroutineScope,
  root: Path,
  context: BuildContext,
  executableFileMatchers: Collection<PathMatcher> = emptyList(),
) {
  val archives = mutableListOf<Path>()
  val binaries = mutableListOf<Path>()

  Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      val relativePath = root.relativize(file)
      val name = file.fileName.toString()
      if (name.endsWith(".jar") || name.endsWith(".zip")) {
        archives.add(file)
      }
      else if (
        isMacLibrary(name) ||
        executableFileMatchers.any { it.matches(relativePath) } ||
        (SystemInfoRt.isUnix && Files.isExecutable(file))
      ) {
        binaries.add(file)
      }
      return FileVisitResult.CONTINUE
    }
  })

  coroutineScope.launch(CoroutineName("signing macOS binaries")) {
    val binariesToSign = binaries.filter { isMacBinary(it) && !isSigned(it) }
    signMacBinaries(binariesToSign, context)
  }

  for (file in archives) {
    coroutineScope.launch(CoroutineName("signing macOS binaries in ${file.relativeTo(root)}")) {
      signAndRepackZipIfMacSignaturesAreMissing(file, context)
    }
  }
}

private suspend fun signAndRepackZipIfMacSignaturesAreMissing(zip: Path, context: BuildContext) {
  val filesToBeSigned = LinkedHashMap<String, Path>()
  suspendAwareReadZipFile(zip) { name, dataSupplier ->
    if (!isMacLibrary(name)) {
      return@suspendAwareReadZipFile
    }

    val data = dataSupplier()
    val byteChannel = ByteBufferChannel(data)
    data.mark()
    if (isMacBinary(byteChannel)) {
      data.reset()
      if (!isSigned(byteChannel, name)) {
        data.reset()
        val fileToBeSigned = Files.createTempFile(context.paths.tempDir, name.replace('/', '-').takeLast(128), "")
        FileChannel.open(fileToBeSigned, WRITE_OPEN_OPTION).use { fileChannel ->
          writeToFileChannelFully(fileChannel, data)
        }
        filesToBeSigned[name] = fileToBeSigned
      }
    }
  }

  if (filesToBeSigned.isEmpty()) {
    return
  }

  signMacBinaries(filesToBeSigned.values.toList(), context, checkPermissions = false)

  copyZipReplacing(origin = zip, entries = filesToBeSigned, context)

  for (file in filesToBeSigned.values) {
    Files.deleteIfExists(file)
  }
}

private suspend fun copyZipReplacing(origin: Path, entries: Map<String, Path>, context: BuildContext) {
  spanBuilder("replacing unsigned entries in zip")
    .setAttribute("zip", origin.toString())
    .setAttribute(AttributeKey.stringArrayKey("unsigned"), entries.keys.toList())
    .use {
      val packageIndexBuilder = PackageIndexBuilder(AddDirEntriesMode.NONE)
      writeZipUsingTempFile(file = origin, packageIndexBuilder) { zipWriter ->
        readZipFile(origin) { name, dataSupplier ->
          packageIndexBuilder.addFile(name)
          if (entries.containsKey(name)) {
            zipWriter.file(name, entries.getValue(name))
          }
          else {
            zipWriter.uncompressedData(name, dataSupplier())
          }
          ZipEntryProcessorResult.CONTINUE
        }
      }
      Files.setLastModifiedTime(origin, FileTime.from(context.options.buildDateInSeconds, TimeUnit.SECONDS))
    }
}

internal fun macSigningOptions(contentType: String, context: BuildContext): PersistentMap<String, String> {
  val certificateID = context.proprietaryBuildTools.signTool.macOsCodesignIdentity?.certificateID
  check(certificateID != null || context.isStepSkipped(BuildOptions.MAC_SIGN_STEP)) { "Missing certificate ID" }
  val entitlements = context.paths.communityHomeDir.resolve("platform/build-scripts/tools/mac/scripts/entitlements.xml")
  check(entitlements.exists()) { "Missing $entitlements file" }
  return persistentMapOf(
    "mac_codesign_options" to "runtime",
    "mac_codesign_identity" to "$certificateID",
    "mac_codesign_entitlements" to "$entitlements",
    "mac_codesign_force" to "true",
    "contentType" to contentType
  )
}

internal suspend fun signMacBinaries(
  files: List<Path>,
  context: BuildContext,
  additionalOptions: Map<String, String> = emptyMap(),
  checkPermissions: Boolean = true,
) {
  if (files.isEmpty() || !context.isMacCodeSignEnabled) {
    return
  }

  check(files.none { it.extension == "sit" })
  val permissions = if (checkPermissions && SystemInfoRt.isUnix) {
    files.associateWith {
      HashSet(Files.getPosixFilePermissions(it))
    }
  }
  else {
    emptyMap()
  }

  val span = spanBuilder("sign binaries for macOS distribution")
  span.setAttribute("contentType", "application/x-mac-app-bin")
  span.setAttribute(AttributeKey.stringArrayKey("files"), files.map { it.name })
  val options = macSigningOptions(contentType = "application/x-mac-app-bin", context).putAll(additionalOptions)
  span.use {
    context.proprietaryBuildTools.signTool.signFiles(files, context, options)
    if (!permissions.isEmpty()) {
      // SRE-1223 workaround
      files.forEach {
        Files.setPosixFilePermissions(it, permissions.getValue(it))
      }
    }

    if (!context.options.isInDevelopmentMode) {
      val missingSignature = files.filter { !isSigned(it) }
      check(missingSignature.isEmpty()) {
        "Missing signature for:\n" + missingSignature.joinToString(separator = "\n\t")
      }
    }
  }
}

private fun isMacBinary(path: Path): Boolean = isMacBinary(Files.newByteChannel(path))

internal suspend fun isSigned(path: Path): Boolean = withContext(Dispatchers.IO) {
  Files.newByteChannel(path).use {
    isSigned(byteChannel = it, binaryId = path.toString())
  }
}

private fun isMacBinary(byteChannel: SeekableByteChannel): Boolean =
  detectFileType(byteChannel).first == FileType.MachO

private fun detectFileType(byteChannel: SeekableByteChannel): Pair<FileType, EnumSet<FileProperties>> = byteChannel.use {
  it.DetectFileType()
}

/**
 * Assumes [isMacBinary].
 */
internal suspend fun isSigned(byteChannel: SeekableByteChannel, binaryId: String): Boolean {
  val verificationParams = SignatureVerificationParams(signRootCertStore = null, timestampRootCertStore = null, buildChain = false, withRevocationCheck = false)
  val binaries = MachoArch(byteChannel).Extract()
  return binaries.all { binary ->
    val signatureData = try {
      binary.GetSignatureData()
    }
    catch (_: InvalidDataException) {
      return@all false
    }

    if (signatureData.IsEmpty) {
      return@all false
    }

    val signedMessage = try {
      SignedMessage.CreateInstance(signatureData)
    }
    catch (_: Exception) {
      // assuming Signature=adhoc
      return@all false
    }

    val result = try {
      val signedMessageVerifier = SignedMessageVerifier(SignatureVerificationLog(binaryId))
      signedMessageVerifier.VerifySignatureAsync(signedMessage, verificationParams)
    }
    catch (e: Exception) {
      throw Exception("Failed to verify $binaryId", e)
    }
    result.Status == VerifySignatureStatus.Valid
  }
}

private class SignatureVerificationLog(val binaryId: String) : ILogger {
  private val span: Span = Span.current()

  fun addEvent(str: String, category: String) {
    span.addEvent(str)
      .setAttribute("binary", binaryId)
      .setAttribute("category", category)
  }

  override fun Info(str: String) = addEvent(str, "INFO")
  override fun Warning(str: String) = addEvent(str, "WARN")
  override fun Error(str: String) = addEvent(str, "ERROR")
  override fun Trace(str: String) {}
}

package com.longerlsx.storyapp.data.book

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun writeFileAtomically(target: File, write: (FileOutputStream) -> Unit) {
    val parent = requireNotNull(target.absoluteFile.parentFile)
    Files.createDirectories(parent.toPath())
    val temporary = File.createTempFile(".${target.name}-", ".tmp", parent)
    try {
        FileOutputStream(temporary).use { output ->
            write(output)
            output.fd.sync()
        }
        Files.move(
            temporary.toPath(), target.toPath(),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
        )
    } finally {
        temporary.delete()
    }
}

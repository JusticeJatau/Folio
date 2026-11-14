package com.example.domain.export

enum class ExportFormat(
    val extension: String,
    val mimeType: String
) {
    PDF(
        extension = "pdf",
        mimeType = "application/pdf"
    ),
    DOCX(
        extension = "docx",
        mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    ),
    TXT(
        extension = "txt",
        mimeType = "text/plain"
    )
}

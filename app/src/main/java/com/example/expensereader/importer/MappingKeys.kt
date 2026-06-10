// File: app/src/main/java/com/example/expensereader/importer/MappingKeys.kt
package com.example.expensereader.importer

object MappingKeys {
    fun refKey(refDigits: String) = "REF:$refDigits"
    fun accKey(accLast4: String) = "ACC:$accLast4"
}

/*
 * Minecraft Dev for IntelliJ
 *
 * https://minecraftdev.org
 *
 * Copyright (c) 2017 minecraft-dev
 *
 * MIT License
 */

package com.demonwav.mcdev.i18n.translations

import com.demonwav.mcdev.i18n.I18nConstants
import com.demonwav.mcdev.i18n.reference.I18nReference
import com.demonwav.mcdev.i18n.translations.identifiers.LiteralTranslationIdentifier
import com.demonwav.mcdev.i18n.translations.identifiers.ReferenceTranslationIdentifier
import com.demonwav.mcdev.platform.mcp.util.McpConstants
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext

data class Translation(val foldingElement: PsiElement?,
                       val referenceElement: PsiElement?,
                       val key: String,
                       val varKey: String,
                       val text: String?,
                       val formattingError: Boolean = false,
                       val containsVariable: Boolean = false) {
    val regexPattern = Regex(varKey.split(I18nReference.VARIABLE_MARKER).map { Regex.escape(it) }.joinToString("(.*?)"))

    companion object {
        val translationFunctions = listOf(
            TranslationFunction(I18nConstants.I18N_CLIENT_CLASS,
                I18nConstants.FORMAT,
                "Ljava.lang.String;[Ljava.lang.Object;",
                0,
                formatting = true,
                obfuscatedName = true),
            TranslationFunction(I18nConstants.I18N_COMMON_CLASS,
                I18nConstants.TRANSLATE_TO_LOCAL,
                "Ljava.lang.String;",
                0,
                formatting = false,
                obfuscatedName = true),
            TranslationFunction(I18nConstants.I18N_COMMON_CLASS,
                I18nConstants.TRANSLATE_TO_LOCAL_FORMATTED,
                "Ljava.lang.String;[Ljava.lang.Object;",
                0,
                formatting = true,
                obfuscatedName = true),
            TranslationFunction(I18nConstants.TRANSLATION_COMPONENT_CLASS,
                I18nConstants.TRANSLATION_COMPONENT_CONSTRUCTOR,
                "Ljava.lang.String;[Ljava.lang.Object;",
                0,
                formatting = true,
                foldParameters = true),
            TranslationFunction(I18nConstants.COMMAND_EXCEPTION_CLASS,
                I18nConstants.COMMAND_EXCEPTION_CONSTRUCTOR,
                "Ljava.lang.String;[Ljava.lang.Object;",
                0,
                formatting = true,
                foldParameters = true),
            TranslationFunction(McpConstants.BLOCK,
                I18nConstants.SET_BLOCK_NAME,
                "Ljava.lang.String;",
                0,
                formatting = false,
                setter = true,
                foldParameters = true,
                prefix = "tile.",
                suffix = ".name",
                obfuscatedName = true),
            TranslationFunction(McpConstants.ITEM,
                I18nConstants.SET_ITEM_NAME,
                "Ljava.lang.String;",
                0,
                formatting = false,
                setter = true,
                foldParameters = true,
                prefix = "item.",
                suffix = ".name",
                obfuscatedName = true))

        private val identifiers = listOf(LiteralTranslationIdentifier(), ReferenceTranslationIdentifier())

        fun find(element: PsiElement): Translation? =
            identifiers.firstOrNull { it.elementClass().isAssignableFrom(element.javaClass) }?.identifyUnsafe(element)

        fun fold(root: PsiElement): Array<FoldingDescriptor> {
            val descriptors = mutableListOf<FoldingDescriptor>()
            for (identifier in identifiers) {
                val elements = PsiTreeUtil.findChildrenOfType(root, identifier.elementClass())
                for (element in elements) {
                    val translation = identifier.identifyUnsafe(element)
                    if (translation != null && translation.foldingElement != null) {
                        val range =
                            if (translation.foldingElement is PsiExpressionList) {
                                TextRange(translation.foldingElement.textRange.startOffset + 1,
                                    translation.foldingElement.textRange.endOffset - 1)
                            } else {
                                TextRange(translation.foldingElement.textRange.startOffset,
                                    translation.foldingElement.textRange.endOffset)
                            }
                        descriptors.add(object : FoldingDescriptor(translation.foldingElement.node,
                            range,
                            FoldingGroup.newGroup("mc.i18n." + translation.key)) {
                            override fun getPlaceholderText(): String? {
                                if (translation.formattingError) {
                                    return "\"Insufficient parameters for formatting '${translation.text}'\""
                                }
                                return "\"${translation.text}\""
                            }
                        })
                    }
                }
            }
            return descriptors.toTypedArray()
        }

        fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
            for (identifier in identifiers) {
                registrar.registerReferenceProvider(
                    PlatformPatterns.psiElement(identifier.elementClass()),
                    object : PsiReferenceProvider() {
                        override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                            val result = identifier.identifyUnsafe(element)
                            if (result != null) {
                                val referenceElement = result.referenceElement ?: return emptyArray()
                                return arrayOf(
                                    I18nReference(referenceElement, TextRange(1, referenceElement.textLength - 1), false, result.key, result.varKey)
                                )
                            }
                            return emptyArray()
                        }
                    })
            }
        }
    }
}


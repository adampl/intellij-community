// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.daemon.ReferenceImporter
import com.intellij.codeInsight.daemon.impl.DaemonListeners
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.actions.createSingleImportAction
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.core.targetDescriptors
import org.jetbrains.kotlin.idea.highlighter.Fe10QuickFixProvider
import org.jetbrains.kotlin.idea.quickfix.ImportFixBase
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class KotlinReferenceImporter : AbstractKotlinReferenceImporter() {
    override fun isEnabledFor(file: KtFile): Boolean = KotlinCodeInsightSettings.getInstance().addUnambiguousImportsOnTheFly
    override val enableAutoImportFilter: Boolean = false
}

abstract class AbstractKotlinReferenceImporter : ReferenceImporter {
    override fun isAddUnambiguousImportsOnTheFlyEnabled(file: PsiFile): Boolean = file is KtFile && isEnabledFor(file)

    protected abstract fun isEnabledFor(file: KtFile): Boolean

    protected abstract val enableAutoImportFilter: Boolean

    private fun filterSuggestions(context: KtFile, suggestions: Collection<FqName>): Collection<FqName> =
        if (enableAutoImportFilter) {
            KotlinAutoImportsFilter.filterSuggestionsIfApplicable(context, suggestions)
        } else {
            suggestions
        }

    override fun autoImportReferenceAtCursor(editor: Editor, file: PsiFile): Boolean {
        return autoImportReferenceAtOffset(editor, file, editor.caretModel.offset)
    }

    override fun autoImportReferenceAtOffset(editor: Editor, file: PsiFile, offset: Int): Boolean {
        if (file !is KtFile || !DaemonListeners.canChangeFileSilently(file)) return false

        fun hasUnresolvedImportWhichCanImport(name: String): Boolean = file.importDirectives.any {
            (it.isAllUnder || it.importPath?.importedName?.asString() == name) && it.targetDescriptors().isEmpty()
        }

        fun KtSimpleNameExpression.autoImport(): Boolean {
            if (hasUnresolvedImportWhichCanImport(getReferencedName())) return false

            val bindingContext = analyze(BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS)
            val diagnostics = bindingContext.diagnostics.filter {
                it.severity == Severity.ERROR
            }.ifEmpty { return false }

            val importFixBases = buildList {
                diagnostics.groupBy { it.psiElement }.forEach { (_, sameElement) ->
                    sameElement.groupBy { it.factory }.forEach { (_, sameTypeDiagnostic) ->
                        val quickFixes = Fe10QuickFixProvider.getInstance(project).createUnresolvedReferenceQuickFixes(sameTypeDiagnostic)
                        for (entry in quickFixes.entrySet()) {
                            for (action in entry.value) {
                                if (action is ImportFixBase<*>) {
                                    add(action)
                                }
                            }
                        }
                    }
                }
            }.ifEmpty { return false }

            val suggestions = filterSuggestions(file, importFixBases.flatMap { it.collectSuggestions() })
                .filter { suggestion ->
                    val descriptors = file.resolveImportReference(suggestion)

                    // we do not auto-import nested classes because this will probably add qualification into the text and this will confuse the user
                    !(descriptors.any { it is ClassDescriptor && it.containingDeclaration is ClassDescriptor })
                }

            var result = false
            CommandProcessor.getInstance().runUndoTransparentAction {
                result = createSingleImportAction(project, editor, this, suggestions).execute()
            }
            return result
        }

        val document = editor.document
        val lineNumber = document.getLineNumber(offset)
        val startOffset = document.getLineStartOffset(lineNumber)
        val endOffset = document.getLineEndOffset(lineNumber)

        return file.elementsInRange(TextRange(startOffset, endOffset))
            .flatMap { it.collectDescendantsOfType<KtSimpleNameExpression>() }
            .any { it.endOffset != offset && it.autoImport() }
    }
}

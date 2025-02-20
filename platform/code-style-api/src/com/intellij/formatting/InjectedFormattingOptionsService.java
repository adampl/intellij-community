// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public interface InjectedFormattingOptionsService {

  static InjectedFormattingOptionsService getInstance() {
    return ApplicationManager.getApplication().getService(InjectedFormattingOptionsService.class);
  }

  boolean shouldDelegateToTopLevel(@NotNull PsiFile file);
}

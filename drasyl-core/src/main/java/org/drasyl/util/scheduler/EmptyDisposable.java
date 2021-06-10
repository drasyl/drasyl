package org.drasyl.util.scheduler;

import io.reactivex.rxjava3.disposables.Disposable;

public final class EmptyDisposable {
    public static final Disposable INSTANCE = io.reactivex.rxjava3.internal.disposables.EmptyDisposable.INSTANCE;

    private EmptyDisposable() {
    }
}

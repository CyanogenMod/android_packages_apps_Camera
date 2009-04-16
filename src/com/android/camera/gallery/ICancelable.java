/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera.gallery;

/**
 * The interface for all the tasks that could be canceled.
 */
public interface ICancelable<T> {
    /*
     * call cancel() when the unit of work in progress needs to be
     * canceled.  This should return true if it was possible to
     * cancel and false otherwise.  If this returns false the caller
     * may still be able to cleanup and simulate cancelation.
     */
    public boolean cancel();

    public T get();
}
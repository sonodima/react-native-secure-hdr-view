package com.sonodima.securehdrview

import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.views.view.ReactViewGroup
import com.facebook.react.views.view.ReactViewManager

@ReactModule(name = SecureHDRViewManager.NAME)
class SecureHDRViewManager : ReactViewManager() {
  override fun getName() = NAME

  override fun createViewInstance(context: ThemedReactContext) = SecureHDRView(context)

  @ReactProp(name = "secure")
  fun setSecure(view: ReactViewGroup, secure: Boolean) {
    (view as SecureHDRView).secure = secure
  }

  @ReactProp(name = "hdr")
  fun setHdr(view: ReactViewGroup, hdr: Float) {
    (view as SecureHDRView).hdr = hdr
  }

  override fun onAfterUpdateTransaction(view: ReactViewGroup) {
    super.onAfterUpdateTransaction(view)
    (view as SecureHDRView).applyProps()
  }

  override fun getChildCount(parent: ReactViewGroup) =
    super.getChildCount(parent) - if ((parent as SecureHDRView).hasSurface) 1 else 0

  override fun removeAllViews(parent: ReactViewGroup) {
    for (i in getChildCount(parent) - 1 downTo 0) {
      removeViewAt(parent, i)
    }
  }

  override fun setRemoveClippedSubviews(
    view: ReactViewGroup,
    removeClippedSubviews: Boolean,
  ) {}

  override fun prepareToRecycleView(
    reactContext: ThemedReactContext,
    view: ReactViewGroup,
  ): ReactViewGroup? = null

  companion object {
    const val NAME = "RNSecureHDRView"
  }
}

package io.slychat.messenger.ios.ui

import apple.NSObject
import apple.uikit.UIButton
import apple.uikit.UILabel
import apple.uikit.UIViewController
import org.moe.natj.general.Pointer
import org.moe.natj.general.ann.Owned
import org.moe.natj.general.ann.RegisterOnStartup
import org.moe.natj.objc.ObjCRuntime
import org.moe.natj.objc.ann.ObjCClassName
import org.moe.natj.objc.ann.Property
import org.moe.natj.objc.ann.Selector

@org.moe.natj.general.ann.Runtime(ObjCRuntime::class)
@ObjCClassName("AppViewController")
@RegisterOnStartup
class AppViewController protected constructor(peer: Pointer) : UIViewController(peer) {

    @Selector("init")
    override external fun init(): AppViewController

    private lateinit var statusText: UILabel
    private lateinit var helloButton: UIButton

    override fun viewDidLoad() {
        statusText = getStatusLabel()
        helloButton = getHelloButton()
    }

    @Selector("statusText")
    @Property
    external fun getStatusLabel(): UILabel

    @Selector("helloButton")
    @Property
    external fun getHelloButton(): UIButton

    @Selector("BtnPressedCancel_helloButton:")
    fun BtnPressedCancel_button(sender: NSObject) {
        statusText.setText("Hello Intel Multi-OS Engine!")
    }

    companion object {
        @Owned
        @Selector("alloc")
        external fun alloc(): AppViewController
    }
}

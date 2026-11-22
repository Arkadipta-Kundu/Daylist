package dev.daylist.benchmark
import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMetricApi::class)
class Performance {
    @get:Rule val benchmark=MacrobenchmarkRule()
    private val pkg="dev.daylist.performance"
    private val phone get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun seed(){phone.executeShellCommand("am start -W -n dev.daylist.performance/dev.daylist.Fixture");phone.pressHome()}
    private fun startup(mode:StartupMode){
        seed()
        benchmark.measureRepeated(pkg,listOf(StartupTimingMetric()),compilationMode=CompilationMode.Full(),iterations=8,startupMode=mode,setupBlock={pressHome()}){
            startActivityAndWait();check(device.wait(Until.hasObject(By.text("Review task 0")),10000))
        }
    }
    @Test fun cold()=startup(StartupMode.COLD)
    @Test fun warm()=startup(StartupMode.WARM)
    @Test fun hot()=startup(StartupMode.HOT)
    @Test fun scrolling()=benchmark.measureRepeated(pkg,listOf(FrameTimingMetric()),compilationMode=CompilationMode.Full(),iterations=6,setupBlock={seed();startActivityAndWait()}){
        repeat(4){device.swipe(device.displayWidth/2,device.displayHeight*3/4,device.displayWidth/2,device.displayHeight/3,20)}
        device.waitForIdle()
    }
    @Test fun captureAndComplete()=benchmark.measureRepeated(pkg,listOf(FrameTimingMetric(),TraceSectionMetric("CreateTask",targetPackageOnly=false),TraceSectionMetric("CompleteTask",targetPackageOnly=false)),compilationMode=CompilationMode.Full(),iterations=6,setupBlock={
        seed();startActivityAndWait();device.wait(Until.findObject(By.desc("New task")),10000).click()
        device.wait(Until.findObject(By.clazz("android.widget.EditText")),10000).text="Bench task today"
        check(device.wait(Until.hasObject(By.desc("Send task").enabled(true)),10000))
    }){
        android.os.Trace.beginSection("CreateTask")
        try{device.findObject(By.desc("Send task")).click();check(device.wait(Until.hasObject(By.text("Bench task")),10000))}finally{android.os.Trace.endSection()}
        android.os.Trace.beginSection("CompleteTask")
        try{device.findObject(By.desc("Complete Bench task")).click();check(device.wait(Until.gone(By.text("Bench task")),10000))}finally{android.os.Trace.endSection()}
    }
}

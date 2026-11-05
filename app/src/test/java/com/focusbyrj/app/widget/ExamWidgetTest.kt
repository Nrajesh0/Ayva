package com.focusbyrj.app.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.R
import com.focusbyrj.app.util.AppThemeManager
import com.focusbyrj.app.util.ThemeMode
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class, qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExamWidgetTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val now = Instant.parse("2026-11-04T09:00:00Z")
    private fun base() = ExamCountdown(name="Civil services", date="2026-12-06", time="09:00", zone="UTC", startDate="2026-10-01", venue="City examination centre", badge="PRELIMS")
    private fun multiple() = base().copy(widgetName="Upcoming exams", extraExams=listOf(
        ExamEvent("second", "Banking exam", "Bring admit card", "2026-11-18", "10:30", "UTC", "2026-10-01", "North campus", "FINAL"),
        ExamEvent("third", "Engineering exam", "You have prepared for this", "2027-01-16", "09:00", "UTC", "2026-10-01")))
    @Before fun setup() {
        app.getSharedPreferences("exam_countdowns",0).edit().clear().commit()
        app.getSharedPreferences("exam_widget_bindings",0).edit().clear().commit()
        AppThemeManager.init(app); AppThemeManager.setThemeMode(app, ThemeMode.OLED)
    }
    @Test fun oldJsonKeepsItsOriginalAppearanceAndHiddenDateBehavior() {
        val original = base().copy(showDate=false)
        val raw = JSONObject(original.json())
        val oldKeys = setOf("name","subtitle","date","time","zone","start","format","palette","background","foreground","accent","opacity","radius","textSize","align","showDate","showProgress","countUp","endedText")
        raw.keys().asSequence().toList().filterNot { it in oldKeys }.forEach(raw::remove)
        val restored=ExamCountdown.read(raw.toString())
        assertEquals(original.name,restored.name); assertEquals("CLASSIC",restored.design)
        assertEquals("SINGLE",restored.displayMode); assertTrue(restored.extraExams.isEmpty()); assertFalse(restored.showEdit)
        assertFalse(restored.showDate); assertFalse(restored.showTime); assertFalse(restored.showZone)
    }
    @Test fun expandedConfigurationRoundTripsEveryExamAndVisualOption() {
        val config=multiple().copy(design="TICKET",displayMode="LIST",palette="OCEAN",font="SERIF",decoration="ORBIT",
            hidePastExams=true,maxRows=5,titleSize=20,padding=24,showTime=false,showZone=false,showPercent=true,
            remainingProgress=true,dateStyle="LONG",clockStyle="24",border=true,urgency=true,urgentDays=14,
            warningDays=60,showEdit=false,tapAction="NONE")
        assertEquals(config,ExamCountdown.read(config.json()))
    }
    @Test fun limitsDuplicateIdsAndInvalidExtraDatesAreRejectedBeforeSaving() {
        assertThrows(IllegalArgumentException::class.java) { multiple().copy(extraExams=List(8){multiple().extraExams[0].copy(id="$it")}).json() }
        assertThrows(IllegalArgumentException::class.java) { multiple().copy(extraExams=listOf(multiple().extraExams[0],multiple().extraExams[0])).json() }
        assertThrows(IllegalArgumentException::class.java) { multiple().copy(extraExams=listOf(multiple().extraExams[0].copy(id="primary"))).json() }
        assertThrows(java.time.DateTimeException::class.java) { multiple().copy(extraExams=listOf(multiple().extraExams[0].copy(date="2026-02-30"))).json() }
        assertThrows(IllegalArgumentException::class.java) { base().copy(urgentDays=30,warningDays=7).json() }
        assertThrows(IllegalArgumentException::class.java) { base().copy(design="UNKNOWN").json() }
    }
    @Test fun selectionSortingFilteringAndAllFinishedFallbackStayUseful() {
        val value=multiple().copy(displayMode="LIST",sortOrder="DATE",maxRows=1)
        assertEquals("second",value.visibleExams(now).first().id)
        assertEquals("second",ExamWidgetPresentation.page(value.copy(displayMode="NEXT"),"third",240,now).entries.single().id)
        val after=Instant.parse("2026-12-25T09:00:00Z")
        assertEquals(listOf("third"),value.copy(hidePastExams=true).visibleExams(after).map{it.id})
        assertEquals("third",value.copy(hidePastExams=true).visibleExams(Instant.parse("2028-01-01T00:00:00Z")).single().id)
        assertEquals("primary",ExamWidgetPresentation.page(value.copy(displayMode="SINGLE"),"second",240,now).entries.single().id)
    }
    @Test fun pageCapacityAdaptsAndMissingSelectionRecovers() {
        val value=multiple().copy(displayMode="LIST",maxRows=6)
        assertEquals(1,ExamWidgetPresentation.page(value,"missing",110,now).entries.size)
        assertTrue(ExamWidgetPresentation.page(value,"primary",600,now).entries.size>1)
        assertTrue(ExamWidgetPresentation.page(value,"primary",360,now,2f).capacity<=ExamWidgetPresentation.page(value,"primary",360,now,1f).capacity)
    }
    @Test fun listPagingWrapsBetweenWholePagesInBothDirections() {
        val value=multiple().copy(displayMode="LIST",maxRows=2,design="CLASSIC",showProgress=true,showTime=true,showZone=true)
        val first=ExamWidgetPresentation.page(value,"primary",420,now)
        assertEquals(2,first.capacity)
        assertEquals("third",first.adjacentId(true))
        assertEquals("third",first.adjacentId(false))
        val last=ExamWidgetPresentation.page(value,"third",420,now)
        assertEquals("primary",last.adjacentId(true));assertEquals("primary",last.adjacentId(false))
        assertEquals("primary",ExamWidgetPresentation.page(value,"second",420,now).entries.first().id)
    }
    @Test fun newSortedListStartsAtTheSoonestExamWithoutLosingExplicitNavigation() {
        val value=multiple().copy(displayMode="LIST",sortOrder="DATE",maxRows=1)
        val store=ExamCountdownStore(app);store.save(101,value)
        assertEquals("second",ExamWidgetPresentation.page(value,store.activeExam(101),140,now).entries.single().id)
        store.setActiveExam(101,"primary")
        assertEquals("primary",ExamWidgetPresentation.page(value,store.activeExam(101),140,now).entries.single().id)
    }
    @Test fun primaryChangesAndRemovalKeepTheOtherExams() {
        val value=multiple().makePrimary("second")
        assertEquals("Banking exam",value.name)
        assertEquals("Civil services",value.extraExams.first().name)
        assertEquals(3,value.exams().map{it.id}.distinct().size)
        val removed=multiple().removeExam("primary")
        assertEquals("Banking exam",removed.name); assertEquals(2,removed.exams().size)
        assertEquals(listOf("primary","third"),removed.exams().map{it.id})
    }
    @Test fun widgetInstancesLayoutsAndRebindingDoNotOverwriteEachOther() {
        val store=ExamCountdownStore(app)
        val first=store.save(101,multiple());val second=store.save(202,multiple())
        assertNotEquals(first,second)
        store.save(101,base().copy(name="Changed"));assertEquals("Civil services",store.get(202)!!.name)
        store.setActiveExam(202,"third");store.rebind(202,303)
        assertNull(store.get(202));assertEquals("third",store.activeExam(303))
        assertFalse(store.deleteTemplate(second))
        val preset=store.saveTemplate(multiple());assertTrue(store.deleteTemplate(preset))
        store.removeWidget(303);assertNull(store.get(303));assertTrue(store.deleteTemplate(second))
    }
    @Test fun visualEditsKeepThePageAndContentEditsResetIt() {
        val store=ExamCountdownStore(app);val value=multiple().copy(displayMode="PAGED")
        store.save(101,value);store.setActiveExam(101,"second")
        store.save(101,value.copy(font="SERIF"));assertEquals("second",store.activeExam(101))
        store.save(101,value.copy(sortOrder="DATE"));assertEquals("",store.activeExam(101))
    }
    @Test fun smallWidgetsWithLargeSystemFontsKeepTheCounterInsideTheWidget() {
        val config=android.content.res.Configuration(app.resources.configuration).apply {fontScale=1.6f}
        val context=app.createConfigurationContext(config)
        for (design in ExamCountdown.DESIGNS) {
            val host=FrameLayout(context)
            val view=ExamCountdownProvider.render(context,101,base().copy(design=design,textSize=64),140,110,now=now).apply(context,host)
            host.addView(view)
            view.measure(View.MeasureSpec.makeMeasureSpec(280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(220,View.MeasureSpec.EXACTLY));view.layout(0,0,280,220)
            val number=view.findViewById<TextView>(R.id.exam_number)
            var top=number.top;var parent=number.parent
            while(parent is View && parent!==view) {top+=parent.top;parent=parent.parent}
            assertTrue("$design counter top $top",top>=0)
            assertTrue("$design counter bottom ${top+number.height}",top+number.height<=220)
        }
    }
    @Test fun navigationIsScopedToTheRequestedWidgetAndSurvivesUpdates() {
        val manager=AppWidgetManager.getInstance(app);val provider=ComponentName(app,ExamCountdownProvider::class.java)
        shadowOf(manager).addInstalledProvider(AppWidgetProviderInfo().apply { this.provider=provider })
        shadowOf(manager).bindAppWidgetId(101,provider);shadowOf(manager).bindAppWidgetId(202,provider)
        val store=ExamCountdownStore(app);store.save(101,multiple().copy(displayMode="PAGED"));store.save(202,multiple())
        ExamCountdownProvider().onReceive(app,Intent(app,ExamCountdownProvider::class.java).setAction(ExamCountdownProvider.NEXT).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,101))
        assertEquals("second",store.activeExam(101));assertEquals("",store.activeExam(202))
        ExamCountdownProvider.update(app,101);assertEquals("second",store.activeExam(101))
        ExamCountdownProvider().onReceive(app,Intent(app,ExamCountdownProvider::class.java).setAction(ExamCountdownProvider.NEXT).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,999))
        assertEquals("second",store.activeExam(101))
    }
    @Test fun weekdayAndMonthFormatsAndProgressRemainDeterministic() {
        val value=ExamCountdown(date="2026-11-09",time="09:00",zone="UTC",startDate="2026-11-01",format="WEEKDAYS")
        assertEquals("3 weekdays left",value.headline(now))
        assertEquals("1mo 2d left",base().copy(format="MONTHS").headline(now))
        assertEquals(100-value.progress(now),ExamWidgetPresentation.progress(value.copy(remainingProgress=true),now))
        assertEquals("2026-11-09 · 9:00 AM",ExamWidgetPresentation.date(value.copy(showZone=false,showTime=true,dateStyle="ISO",clockStyle="12"),false))
    }
    @Test fun cleanDefaultsAndLegacyDocumentsKeepTheirOwnPreferences() {
        val clean=ExamCountdown()
        assertEquals("HERO",clean.design);assertEquals("CENTER",clean.align)
        assertFalse(clean.showTime);assertFalse(clean.showZone);assertFalse(clean.showEdit)
        val old=base().copy(design="TICKET",font="SERIF",showTime=true,showZone=true,showProgress=true,showEdit=true,dateStyle="ISO",align="END")
        assertEquals(old,ExamCountdown.read(old.json()))
        val legacy=JSONObject(old.json()).apply {
            listOf("design","font","showTime","showZone","showEdit","dateStyle").forEach {remove(it)}
        }
        val restored=ExamCountdown.read(legacy.toString())
        assertEquals("CLASSIC",restored.design);assertEquals("ISO",restored.dateStyle)
        assertTrue(restored.showTime);assertTrue(restored.showZone);assertFalse(restored.showEdit)
    }
    @Test fun newWideListsShowTwoExamsAndPageAllThreeWithoutLosingAny() {
        val two=multiple().copy(displayMode="LIST",widgetName="",extraExams=multiple().extraExams.take(1))
        val page=ExamWidgetPresentation.page(two,"",180,now)
        assertEquals(2,page.entries.size);assertFalse(page.canPage)
        val three=multiple().copy(displayMode="LIST",widgetName="")
        val first=ExamWidgetPresentation.page(three,"",180,now)
        assertEquals(2,first.entries.size);assertTrue(first.canPage)
        val second=ExamWidgetPresentation.page(three,first.adjacentId(true),180,now)
        assertEquals(listOf("third"),second.entries.map{it.id})
        assertEquals("primary",second.adjacentId(true))
    }
    @Test fun typographyKeepsFinishedMessagesAndAccessibleCountdownMeaning() {
        assertEquals("32" to "days to go",ExamWidgetPresentation.counter(base(),now))
        val after=Instant.parse("2026-12-08T09:00:00Z")
        assertEquals("2" to "days since exam",ExamWidgetPresentation.counter(base().copy(countUp=true),after))
        assertEquals("Nothing left" to "",ExamWidgetPresentation.counter(base().copy(endedText="Nothing left"),after))
        val view=apply(base(),height=180)
        assertEquals("32 days left",view.findViewById<TextView>(R.id.exam_number).contentDescription)
        assertEquals("days to go",view.findViewById<TextView>(R.id.exam_unit).text.toString())
    }
    @Test fun presetChangesAreVisualAndDoNotOverwriteTheExamSchedule() {
        val original=multiple().copy(font="SERIF",decoration="ORBIT",venue="Hall 4",showTime=true,showZone=true,transparent=true)
        val changed=original.withDesign("TIMELINE")
        assertEquals(original.exams(),changed.exams());assertEquals(original.palette,changed.palette)
        assertTrue(changed.showTime);assertTrue(changed.showZone);assertTrue(changed.transparent)
        assertEquals("SANS",changed.font);assertEquals("NONE",changed.decoration);assertTrue(changed.showProgress)
    }
    @Test fun captureModernLayoutsWithoutDecorativeExtras() {
        for(design in listOf("HERO","CLASSIC","TIMELINE","MINIMAL")) {
            val config=ExamCountdown(name="Civil services",date="2026-12-06",time="09:00",zone="UTC",startDate="2026-10-01",palette="DARK").withDesign(design)
            capture(apply(config,width=340,height=180),"modern-${design.lowercase()}")
        }
        capture(apply(multiple().copy(displayMode="LIST",palette="LIGHT",badge="",venue="",widgetName="Exam season"),height=180),"modern-agenda")
    }
    @Test fun boldPresetsRemainPortableAndKeepEveryExam() {
        for(design in ExamCountdown.BOLD_DESIGNS) {
            val value=multiple().withDesign(design)
            assertEquals(multiple().exams(),value.exams())
            assertEquals(value,ExamCountdown.read(value.json()))
            capture(apply(value.copy(badge="",venue=""),height=240),"bold-${design.lowercase()}")
            capture(apply(value.copy(displayMode="LIST",widgetName="Exam season"),height=240),"bold-list-${design.lowercase()}")
            capture(apply(value.copy(badge="",venue=""),height=180),"bold-wide-${design.lowercase()}")
        }
    }
    @Test fun posterAndPanelInkStayReadableAndCustomColorsStillWork() {
        for(palette in ExamCountdown.PALETTES.filter { it!="CUSTOM" }) {
            val value=base().withDesign("POSTER").copy(palette=palette)
            val colors=ExamCountdownProvider.surfaceColors(value,ExamCountdownProvider.colors(app,value))
            assertTrue("Poster $palette",androidx.core.graphics.ColorUtils.calculateContrast(colors[1],colors[0])>=4.5)
            val panel=ExamCountdownProvider.contrastInk(colors[2])
            assertTrue("Panel $palette",androidx.core.graphics.ColorUtils.calculateContrast(panel,colors[2])>=4.5)
        }
        val custom=base().withDesign("POSTER").copy(palette="CUSTOM",background="#112233",foreground="#FAFAFA",accent="#AABBCC")
        val colors=ExamCountdownProvider.surfaceColors(custom,ExamCountdownProvider.colors(app,custom))
        assertEquals(android.graphics.Color.parseColor("#112233"),colors[0]);assertEquals(android.graphics.Color.parseColor("#FAFAFA"),colors[1])
    }
    @Test fun transparentOrbitKeepsItsRingAndUsesTheVisibleExamsTimeline() {
        val config=multiple().withDesign("ORBITAL").copy(transparent=true,displayMode="PAGED")
        val visible=config.withEvent(config.extraExams.first())
        val view=ExamCountdownProvider.render(app,101,config,340,240,"second",now).apply(app,FrameLayout(app))
        assertTrue(view.findViewById<TextView>(R.id.exam_number).contentDescription.toString().contains("${visible.progress(now)}%"))
        val c=ExamCountdownProvider.colors(app,config)
        val track=ExamCountdownProvider.background(config,c,340,240,0)
        val filled=ExamCountdownProvider.background(config,c,340,240,50)
        val x=(filled.width*.255f+filled.width*.195f).toInt();val y=filled.height/2
        assertTrue(android.graphics.Color.alpha(filled.getPixel(x,y))>android.graphics.Color.alpha(track.getPixel(x,y)))
        assertEquals(0,android.graphics.Color.alpha(filled.getPixel(filled.width-20,filled.height/2)))
        track.recycle();filled.recycle()
    }
    @Test fun glassPresetsStayTranslucentPortableAndReadableOnLightBackdrops() {
        for (design in ExamCountdown.GLASS_DESIGNS) {
            val config=base().withDesign(design).copy(badge="",venue="")
            assertEquals(config,ExamCountdown.read(config.json()))
            assertEquals(base().copy(badge="",venue="").exams(),config.exams())
            val colors=ExamCountdownProvider.colors(app,config)
            val bitmap=ExamCountdownProvider.background(config,colors,340,180)
            val pixel=bitmap.getPixel(bitmap.width/2,bitmap.height/2)
            assertTrue("$design alpha",android.graphics.Color.alpha(pixel) in 1..254)
            val onWhite=androidx.core.graphics.ColorUtils.compositeColors(pixel,android.graphics.Color.WHITE)
            assertTrue("$design text on white",androidx.core.graphics.ColorUtils.calculateContrast(colors[1],onWhite)>=4.5)
            bitmap.recycle()
            val clear=ExamCountdownProvider.background(config.copy(transparent=true,border=false),colors,340,180)
            assertEquals(0,android.graphics.Color.alpha(clear.getPixel(clear.width/2,clear.height/2)));clear.recycle()
            val dark=apply(config,height=180);capture(dark,"glass-${design.lowercase()}-dark")
            val light=apply(config,height=180);light.background=ExamPreviewWallpaper(true);capture(light,"glass-${design.lowercase()}-light")
            capture(apply(config.copy(displayMode="LIST",extraExams=multiple().extraExams,widgetName="Exam season"),height=280),"glass-list-${design.lowercase()}")
        }
    }
    @Test fun auroraDetailsRemainReadableAcrossItsGlow() {
        val config=base().withDesign("AURORA").copy(badge="",venue="")
        val view=apply(config,height=180)
        val foreground=view.findViewById<TextView>(R.id.exam_date).currentTextColor
        val colors=ExamCountdownProvider.colors(app,config)
        val bitmap=ExamCountdownProvider.background(config,colors,340,180)
        for(x in listOf(.38f,.5f,.62f)) {
            val pixel=bitmap.getPixel((bitmap.width*x).toInt(),(bitmap.height*.84f).toInt())
            assertTrue("Glow date contrast at $x",androidx.core.graphics.ColorUtils.calculateContrast(foreground,pixel)>=4.5)
        }
        bitmap.recycle()
    }
    @Test fun streamlinedCatalogAndNewWidgetDefaultsKeepLegacyBackupsReadable() {
        assertEquals(setOf("POSTER","ORBITAL","AURORA","BENTO","SIGNAL","FROST","PRISM","LAYERS"),ExamCountdown.ACTIVE_DESIGNS.toSet())
        assertEquals("AURORA",ExamCountdown.newWidget().design)
        ExamCountdown.newWidget().validate()
        for(design in ExamCountdown.LEGACY_DESIGNS) {
            val old=multiple().copy(design=design,font="SERIF",showTime=true,align="END")
            assertFalse(design in ExamCountdown.ACTIVE_DESIGNS)
            assertEquals(old,ExamCountdown.read(old.json()))
        }
    }
    @Test fun oneExamListUsesTheFullDesignAndNeverPrintsTimezoneText() {
        val event=ExamCountdown(name="My exam",date="2026-11-21",time="23:00",zone="Asia/Kolkata",startDate="2026-11-01",
            design="LAYERS",displayMode="LIST",format="HOURS",showTime=true,showZone=true,dateStyle="ISO").withDesign("LAYERS")
        for(design in ExamCountdown.ACTIVE_DESIGNS) {
            val view=apply(event.copy(design=design),height=260)
            assertEquals(View.GONE,view.findViewById<View>(R.id.exam_list).visibility)
            assertEquals(View.VISIBLE,view.findViewById<View>(R.id.exam_primary).visibility)
            assertEquals(View.GONE,view.findViewById<View>(R.id.exam_toolbar).visibility)
            assertTrue(view.findViewById<TextView>(R.id.exam_number).textSize>=28*app.resources.displayMetrics.scaledDensity)
            assertFalse(view.findViewById<TextView>(R.id.exam_date).text.toString().contains("Kolkata"))
            assertFalse(ExamWidgetPresentation.date(event,true).contains("Asia"))
        }
        capture(apply(event,height=260),"single-list-fixed")
        capture(apply(event.withEssentialDetails(),height=260),"single-list-essentials")
        assertEquals("Asia/Kolkata",ExamCountdown.read(event.json()).zone)
    }
    @Test fun actualWidgetSizesUsePairsAndKeepNavigationOnTheVisiblePage() {
        val options=android.os.Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,240);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,400)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,110);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,300)
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(android.util.SizeF(240f,300f),android.util.SizeF(400f,110f)))
        }
        val portrait=app.createConfigurationContext(android.content.res.Configuration(app.resources.configuration).apply{orientation=android.content.res.Configuration.ORIENTATION_PORTRAIT})
        val landscape=app.createConfigurationContext(android.content.res.Configuration(app.resources.configuration).apply{orientation=android.content.res.Configuration.ORIENTATION_LANDSCAPE})
        assertEquals(android.util.SizeF(240f,300f),ExamWidgetSizes.current(portrait,options))
        assertEquals(android.util.SizeF(400f,110f),ExamWidgetSizes.current(landscape,options))
        val manager=AppWidgetManager.getInstance(portrait);val provider=ComponentName(app,ExamCountdownProvider::class.java)
        shadowOf(manager).addInstalledProvider(AppWidgetProviderInfo().apply{this.provider=provider});shadowOf(manager).bindAppWidgetId(101,provider)
        manager.updateAppWidgetOptions(101,options)
        val value=multiple().copy(displayMode="LIST",maxRows=2,showBadge=false)
        ExamCountdownStore(app).save(101,value)
        ExamCountdownProvider().onReceive(portrait,Intent(app,ExamCountdownProvider::class.java).setAction(ExamCountdownProvider.NEXT).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,101))
        assertEquals("third",ExamCountdownStore(app).activeExam(101))
    }
    @Test fun compactListsAndLargeFontsKeepEveryVisibleTextInsideTheWidget() {
        for(scale in listOf(1f,1.6f,2f)) {
            val context=app.createConfigurationContext(android.content.res.Configuration(app.resources.configuration).apply{fontScale=scale})
            val view=ExamCountdownProvider.render(context,101,multiple().copy(displayMode="LIST",showZone=true),140,110,now=now).apply(context,FrameLayout(context))
            view.measure(View.MeasureSpec.makeMeasureSpec(280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(220,View.MeasureSpec.EXACTLY));view.layout(0,0,280,220)
            capture(view,"compact-list-${scale}")
        }
    }
    @Test fun previewImagesAreProductionRendersForTheSameDataAndSize() {
        val value=multiple().withDesign("FROST").copy(displayMode="LIST",showZone=true)
        val bitmap=examPreviewBitmap(app,value,340,300,"",false,now)
        val view=apply(value,width=340,height=300)
        val expected=Bitmap.createBitmap(bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(expected);canvas.scale(bitmap.width/view.width.toFloat(),bitmap.width/view.width.toFloat());view.draw(canvas)
        assertTrue("Thumbnail must match the live RemoteViews render",bitmap.sameAs(expected))
        bitmap.recycle();expected.recycle()
    }
    @Test fun paginationUsesTheSameScaleAsTheRendererWithSmallSystemFonts() {
        val config=multiple().copy(displayMode="LIST")
        assertEquals(ExamWidgetPresentation.page(config,"",180,now,1f),ExamWidgetPresentation.page(config,"",180,now,.85f))
    }
    @Test fun largeWidgetSurfacesKeepTheirAspectRatioWithoutAllocatingHugeBitmaps() {
        val config=base().withDesign("BENTO");val colors=ExamCountdownProvider.colors(app,config)
        val wide=ExamCountdownProvider.background(config,colors,900,300)
        assertEquals(wide.width*300,wide.height*900)
        assertTrue(wide.width<=1200 && wide.height<=1200);wide.recycle()
        val large=ExamCountdownProvider.background(config,colors,2000,2000)
        assertTrue(large.width*large.height<=1200*1200);large.recycle()
    }
    private fun apply(value: ExamCountdown,width:Int=340,height:Int=300):View {
        val host=FrameLayout(app)
        val view=ExamCountdownProvider.render(app,101,value,width,height,now=now).apply(app,host)
        host.addView(view)
        if(value.design in ExamCountdown.GLASS_DESIGNS) view.background=ExamPreviewWallpaper()
        view.measure(View.MeasureSpec.makeMeasureSpec(width*2,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height*2,View.MeasureSpec.EXACTLY))
        view.layout(0,0,width*2,height*2)
        return view
    }
    @Test fun everyDesignAndThemeRendersRealRemoteViews() {
        ExamCountdown.DESIGNS.forEach { design ->
            val config=multiple().copy(design=design,palette="OCEAN",decoration="ORBIT",showPercent=true)
            val view=apply(config)
            assertEquals("Civil services",view.findViewById<TextView>(R.id.exam_name).text.toString())
            assertTrue(view.findViewById<TextView>(R.id.exam_number).text.isNotBlank())
            capture(view,"design-${design.lowercase()}")
            capture(apply(config.copy(displayMode="LIST"),height=420),"list-${design.lowercase()}")
        }
        ExamCountdown.PALETTES.forEach { theme -> apply(base().copy(palette=theme)) }
    }
    @Test fun listAndCompactLayoutsRenderAndRespectDetailToggles() {
        val config=multiple().copy(displayMode="LIST",sortOrder="DATE",showMessage=true)
        val large=apply(config,height=420); capture(large,"exam-list")
        val small=apply(config,height=140);capture(small,"exam-list-small")
        assertEquals(View.VISIBLE,small.findViewById<View>(R.id.exam_next).visibility)
        val single=apply(base().copy(showTitle=false,showMessage=false,showDate=false,showTime=false,showZone=false,showVenue=false,showProgress=false),height=140)
        assertEquals(View.GONE,single.findViewById<View>(R.id.exam_name).visibility)
        assertEquals(View.GONE,single.findViewById<View>(R.id.exam_date).visibility)
        capture(single,"compact")
    }
    @Test fun transparentBackgroundAndUrgencyColorsWork() {
        val bitmap=ExamCountdownProvider.background(base().copy(transparent=true,border=false),intArrayOf(-1,-1,-1),200,140)
        assertEquals(0,android.graphics.Color.alpha(bitmap.getPixel(100,100)));bitmap.recycle()
        val normal=ExamCountdownProvider.colors(app,base())[2]
        assertEquals(normal,ExamCountdownProvider.accent(base(),normal,now))
        assertNotEquals(normal,ExamCountdownProvider.accent(base().copy(urgency=true,warningDays=60),normal,now))
    }
    private fun capture(view:View,name:String) {
        val bounds=StringBuilder()
        fun inspect(node:View,x:Int=0,y:Int=0) {
            if(node.visibility!=View.VISIBLE)return
            node.invalidate()
            if(node is TextView && node.text.isNotBlank()) {
                assertTrue("$name ${node.text} top",y+node.top>=0)
                assertTrue("$name ${node.text} bottom",y+node.top+node.height<=view.height)
                bounds.append("${node.text}: ${x+node.left},${y+node.top} ${node.width}x${node.height}\n")
            }
            if(node is android.view.ViewGroup) for(i in 0 until node.childCount) inspect(node.getChildAt(i),x+node.left,y+node.top)
        }
        inspect(view)
        File("build/reports/exam-widgets/$name-bounds.txt").apply { parentFile!!.mkdirs();writeText(bounds.toString()) }
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);view.draw(canvas);canvas.setBitmap(null)
        val file=File("build/reports/exam-widgets/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
}

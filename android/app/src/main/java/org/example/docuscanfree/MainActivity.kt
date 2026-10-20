package org.example.docuscanfree

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.googlecode.tesseract.android.TessBaseAPI
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private data class ScanPage(val name: String, val original: Bitmap, var edited: Bitmap, var ocr: String = "")

class MainActivity : AppCompatActivity() {
    private val pickCode = 41; private val rawCaptureCode = 48; private val saveProjectCode = 43; private val openProjectCode = 44
    private val exportPdfCode = 45; private val exportTextCode = 46
    private val pages = mutableListOf<ScanPage>()
    private var current = -1
    private lateinit var preview: ImageView; private lateinit var status: TextView
    private lateinit var extracted: EditText; private lateinit var pagePicker: Spinner
    private lateinit var pageCount: TextView
    private var syncingPicker = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7, 27, 53)
        window.navigationBarColor = android.graphics.Color.rgb(7, 27, 53)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 12, 16, 12); setBackgroundColor(0xFFF3F7FB.toInt()) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 16, 18, 16); setBackgroundColor(0xFF0B3954.toInt()) }
        header.addView(TextView(this).apply { text = "SHAHAB SCANNER"; textSize = 22f; setTextColor(-1); gravity = Gravity.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD })
        header.addView(TextView(this).apply { text = "اسکن حرفه‌ای • OCR فارسی • پروژه قابل‌انتقال"; textSize = 14f; setTextColor(0xFFD9F2FF.toInt()); gravity = Gravity.CENTER; setPadding(0, 6, 0, 0) })
        root.addView(header, LinearLayout.LayoutParams(-1, -2))
        pageCount = TextView(this).apply { text = "هنوز صفحه‌ای اضافه نشده"; textSize = 14f; setTextColor(0xFF0B3954.toInt()); setPadding(2, 10, 2, 4) }; root.addView(pageCount)
        pagePicker = Spinner(this); root.addView(pagePicker, LinearLayout.LayoutParams(-1, -2))
        pagePicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { if (!syncingPicker && position in pages.indices) { syncOcr(); showPage(position) } }
        }
        val grid = GridLayout(this).apply { columnCount = 2; useDefaultMargins = true }
        fun action(label: String, callback: () -> Unit) { val b = Button(this).apply { text = label; isAllCaps = false; setTextColor(0xFF0B3954.toInt()); setOnClickListener { callback() } }; grid.addView(b, GridLayout.LayoutParams().apply { width = 0; height = -2; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(3, 3, 3, 3) }) }
        action("📷 عکس خام دوربین (بدون برش)", ::startRawCamera); action("＋ اسکن زنده چندصفحه‌ای", ::startScanner); action("＋ انتخاب تصویر", ::pickImage)
        action("↻ چرخش ۹۰°", { transform(true, false) }); action("◐ خاکستری", { transform(false, true) }); action("بازگشت به اصل تصویر", ::restoreOriginal)
        action("حذف صفحه", ::deletePage); action("OCR فارسی / انگلیسی", ::runOcr); action("خروجی PDF چندصفحه‌ای", ::requestPdf)
        action("ذخیره پروژه .shahab", ::requestSaveProject); action("بازکردن پروژه .shahab", ::requestOpenProject); action("ذخیره متن OCR", ::requestSaveText)
        root.addView(grid)
        preview = ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER; minimumHeight = 180; setBackgroundColor(0xFFE1EAF1.toInt()) }
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        status = TextView(this).apply { text = "برای شروع اسکن کنید یا تصویر انتخاب کنید."; textSize = 13f; setTextColor(0xFF24465B.toInt()); setPadding(2, 8, 2, 4) }; root.addView(status)
        extracted = EditText(this).apply { hint = "متن OCR صفحه انتخاب‌شده (قابل ویرایش)"; gravity = Gravity.TOP or Gravity.START; minLines = 2; maxLines = 4; textSize = 14f; setTextColor(0xFF182B3A.toInt()) }
        root.addView(extracted, LinearLayout.LayoutParams(-1, 105)); setContentView(root)
    }
    private fun startRawCamera() { startActivityForResult(Intent(this, RawCaptureActivity::class.java), rawCaptureCode) }
    private fun startScanner() {
        val opts = GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(100).setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, GmsDocumentScannerOptions.RESULT_FORMAT_PDF).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
        status.text = "در حال آماده‌سازی اسکنر..."
        GmsDocumentScanning.getClient(opts).getStartScanIntent(this).addOnSuccessListener { sender -> try { startIntentSenderForResult(sender, 47, null, 0, 0, 0) } catch (e: Exception) { status.text = "اجرای اسکنر ناموفق بود: ${e.localizedMessage}" } }.addOnFailureListener { status.text = "اسکنر در دسترس نیست. Google Play Services را به‌روز کنید. ${it.localizedMessage ?: ""}" }
    }
    private fun pickImage() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }, pickCode) }
    private fun requestSaveProject() { if (pages.isEmpty()) { status.text = "ابتدا صفحه اضافه کنید."; return }; startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip"; putExtra(Intent.EXTRA_TITLE, "ShahabProject.shahab") }, saveProjectCode) }
    private fun requestOpenProject() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip" }, openProjectCode) }
    private fun requestPdf() { if (pages.isEmpty()) { status.text = "صفحه‌ای برای PDF وجود ندارد."; return }; startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/pdf"; putExtra(Intent.EXTRA_TITLE, "ShahabScanner.pdf") }, exportPdfCode) }
    private fun requestSaveText() { syncOcr(); if (pages.none { it.ocr.isNotBlank() }) { status.text = "متن OCR موجود نیست."; return }; startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "text/plain"; putExtra(Intent.EXTRA_TITLE, "Shahab_OCR.txt") }, exportTextCode) }
    @Deprecated("Legacy result API retained for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data); if (resultCode != Activity.RESULT_OK) return
        try { when (requestCode) {
            rawCaptureCode -> { val path = data?.getStringExtra(RawCaptureActivity.EXTRA_RAW_PATH) ?: return; val file = File(path); if (!file.exists() || file.length() < 1024) error("فایل عکس خام پیدا نشد"); val raw = BitmapFactory.decodeFile(file.absolutePath) ?: error("خواندن عکس خام ممکن نیست"); pages.add(ScanPage(data.getStringExtra(RawCaptureActivity.EXTRA_RAW_NAME) ?: "Raw_${pages.size+1}.jpg", raw.copy(Bitmap.Config.ARGB_8888, false), raw.copy(Bitmap.Config.ARGB_8888, true))); refreshPages(pages.lastIndex); status.text = "عکس خام دوربین ذخیره شد (${raw.width}×${raw.height}). نسخه اصلی و ویرایشی جدا هستند."; file.delete() }
            47 -> { val result = GmsDocumentScanningResult.fromActivityResultIntent(data) ?: error("نتیجه اسکن خوانده نشد"); val newPages = result.pages ?: emptyList(); if (newPages.isEmpty()) error("اسکنر تصویری برنگرداند"); newPages.forEach { p -> contentResolver.openInputStream(p.imageUri)?.use { stream -> val b = BitmapFactory.decodeStream(stream) ?: error("تصویر صفحه قابل خواندن نیست"); pages.add(ScanPage("Scan_${pages.size+1}.jpg", b.copy(Bitmap.Config.ARGB_8888, true), b)) } }; refreshPages(pages.size-newPages.size); status.text = "${newPages.size} صفحه اسکن شد." }
            pickCode -> { val uri = data?.data ?: return; contentResolver.openInputStream(uri)?.use { val b = BitmapFactory.decodeStream(it) ?: error("تصویر نامعتبر است"); pages.add(ScanPage("Image_${pages.size+1}", b.copy(Bitmap.Config.ARGB_8888, true), b)); refreshPages(pages.lastIndex) } }
            saveProjectCode -> { val uri=data?.data ?: return; syncOcr(); contentResolver.openOutputStream(uri)?.use { out -> ZipOutputStream(BufferedOutputStream(out)).use { zip -> val manifest=JSONObject().put("format","ShahabScannerProject").put("version",1); val arr=JSONArray(); pages.forEachIndexed { i,p -> val orig="pages/${i.toString().padStart(4,'0')}_original.png"; val edit="pages/${i.toString().padStart(4,'0')}_edited.png"; putBitmap(zip,orig,p.original); putBitmap(zip,edit,p.edited); arr.put(JSONObject().put("name",p.name).put("original",orig).put("edited",edit).put("ocr",p.ocr)) }; manifest.put("pages",arr); putText(zip,"project.json",manifest.toString(2)); putText(zip,"ocr.txt",pages.mapIndexed { i,p -> "--- صفحه ${i+1}: ${p.name} ---\n${p.ocr}" }.joinToString("\n\n")) } }; status.text="پروژه ${pages.size} صفحه‌ای ذخیره شد." }
            openProjectCode -> { val loaded=loadProject(data?.data ?: return); pages.clear(); pages.addAll(loaded); refreshPages(0); status.text="پروژه باز شد: ${pages.size} صفحه." }
            exportPdfCode -> { contentResolver.openOutputStream(data?.data ?: return)?.use { writePdf(it) }; status.text="PDF چندصفحه‌ای ذخیره شد." }
            exportTextCode -> { syncOcr(); contentResolver.openOutputStream(data?.data ?: return)?.bufferedWriter(Charsets.UTF_8)?.use { w -> pages.forEachIndexed { i,p -> w.write("--- صفحه ${i+1}: ${p.name} ---\n${p.ocr}\n\n") } }; status.text="متن OCR ذخیره شد." }
        } } catch (e: Exception) { status.text = "خطا: ${e.localizedMessage ?: e.javaClass.simpleName}" }
    }
    private fun putBitmap(zip: ZipOutputStream,path:String,b:Bitmap) { zip.putNextEntry(ZipEntry(path)); if(!b.compress(Bitmap.CompressFormat.PNG,100,zip)) error("ذخیره تصویر شکست خورد"); zip.closeEntry() }
    private fun putText(zip:ZipOutputStream,path:String,text:String) { zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
    private fun loadProject(uri:Uri):List<ScanPage> {
        val entries=mutableMapOf<String,ByteArray>(); var total=0L
        contentResolver.openInputStream(uri)?.use { input -> ZipInputStream(BufferedInputStream(input)).use { z -> var e=z.nextEntry; while(e!=null) { val name=e.name; if(name.startsWith("/") || name.split('/').any { it==".." }) error("مسیر نامعتبر داخل پروژه"); if(!e.isDirectory) { val bytes=z.readBytes(); total+=bytes.size; if(total>300L*1024*1024) error("حجم پروژه از ۳۰۰ مگابایت بیشتر است"); entries[name]=bytes }; z.closeEntry(); e=z.nextEntry } } } ?: error("فایل باز نشد")
        val json=JSONObject(entries["project.json"]?.toString(Charsets.UTF_8) ?: error("project.json وجود ندارد")); if(json.optString("format")!="ShahabScannerProject") error("فرمت پروژه معتبر نیست")
        val arr=json.optJSONArray("pages") ?: error("فهرست صفحات وجود ندارد"); if(arr.length() !in 1..100) error("تعداد صفحات باید بین ۱ تا ۱۰۰ باشد")
        val out=mutableListOf<ScanPage>(); for(i in 0 until arr.length()) { val p=arr.getJSONObject(i); val ob=entries[p.getString("original")] ?: error("تصویر اصلی صفحه ${i+1} وجود ندارد"); val eb=entries[p.optString("edited",p.getString("original"))] ?: ob; val original=BitmapFactory.decodeByteArray(ob,0,ob.size) ?: error("تصویر اصلی خراب است"); val edited=BitmapFactory.decodeByteArray(eb,0,eb.size) ?: original.copy(Bitmap.Config.ARGB_8888,true); out.add(ScanPage(p.optString("name","صفحه ${i+1}"),original,edited,p.optString("ocr",""))) }; return out
    }
    private fun refreshPages(select:Int) { syncingPicker=true; pagePicker.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,pages.mapIndexed { i,p -> "${i+1}. ${p.name}" }); syncingPicker=false; pageCount.text="${pages.size} صفحه در پروژه"; if(pages.isEmpty()) { current=-1; preview.setImageDrawable(null); extracted.setText(""); return }; showPage(select.coerceIn(0,pages.lastIndex)); pagePicker.setSelection(current) }
    private fun showPage(index:Int) { if(index !in pages.indices) return; current=index; preview.setImageBitmap(pages[index].edited); extracted.setText(pages[index].ocr); pageCount.text="صفحه ${index+1} از ${pages.size}" }
    private fun syncOcr() { if(current in pages.indices) pages[current].ocr=extracted.text.toString() }
    private fun transform(rotate:Boolean,gray:Boolean) { if(current !in pages.indices) { status.text="ابتدا صفحه اضافه کنید."; return }; syncOcr(); val src=pages[current].edited; pages[current].edited=if(rotate) Bitmap.createBitmap(src,0,0,src.width,src.height,Matrix().apply { postRotate(90f) },true) else Bitmap.createBitmap(src.width,src.height,Bitmap.Config.ARGB_8888).also { out -> Canvas(out).drawBitmap(src,0f,0f,Paint().apply { colorFilter=ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }) }; preview.setImageBitmap(pages[current].edited); status.text=if(rotate) "چرخش اعمال شد؛ تصویر اصلی حفظ شده است." else "فیلتر خاکستری اعمال شد." }
    private fun restoreOriginal() { if(current !in pages.indices) return; pages[current].edited=pages[current].original.copy(Bitmap.Config.ARGB_8888,true); preview.setImageBitmap(pages[current].edited); status.text="نسخه مادر بازیابی شد." }
    private fun deletePage() { if(current !in pages.indices) return; pages.removeAt(current); refreshPages(if(pages.isEmpty()) -1 else current.coerceAtMost(pages.lastIndex)); status.text="صفحه حذف شد." }
    private fun runOcr() { if(current !in pages.indices) { status.text="ابتدا صفحه اضافه کنید."; return }; syncOcr(); val index=current; val bitmap=pages[index].edited.copy(Bitmap.Config.ARGB_8888,false); status.text="در حال آماده‌سازی مدل OCR فارسی؛ در اولین اجرا اینترنت لازم است..."
        Thread { try { val dataDir=File(filesDir,"tesseract"); val tess=File(dataDir,"tessdata"); if(!tess.exists()) tess.mkdirs(); val model=File(tess,"fas.traineddata")
            if(!model.exists() || model.length()<1000000L) { val tmp=File(tess,"fas.traineddata.part"); val conn=(URL("https://github.com/tesseract-ocr/tessdata_fast/raw/main/fas.traineddata").openConnection() as HttpURLConnection).apply { connectTimeout=20000; readTimeout=60000; instanceFollowRedirects=true }; try { conn.inputStream.use { input -> FileOutputStream(tmp).use { output -> val buf=ByteArray(32768); var n:Int; var size=0L; while(input.read(buf).also { n=it } > 0) { size+=n; if(size>80L*1024*1024) error("مدل OCR بیش از حد بزرگ است"); output.write(buf,0,n) } } }; if(tmp.length()<1000000L) error("دانلود مدل OCR ناقص است"); if(model.exists()) model.delete(); if(!tmp.renameTo(model)) error("ذخیره مدل OCR شکست خورد") } finally { conn.disconnect(); tmp.delete() } }
            val api=TessBaseAPI(); if(!api.init(dataDir.absolutePath,"fas")) error("راه‌اندازی مدل OCR ناموفق بود"); api.setImage(bitmap); val text=api.getUTF8Text() ?: ""; api.clear(); api.end(); runOnUiThread { if(index<pages.size) { pages[index].ocr=text; if(current==index) extracted.setText(text); status.text="OCR پایان یافت؛ نتیجه را بازبینی کنید." } }
        } catch(e:Exception) { runOnUiThread { status.text="OCR اجرا نشد: ${e.localizedMessage}" } } }.start()
    }
    private fun writePdf(output:OutputStream) { val doc=PdfDocument(); try { pages.forEachIndexed { i,p -> val b=p.edited; val scale=minOf(1f,2400f/maxOf(b.width,b.height).toFloat()); val w=(b.width*scale).toInt().coerceAtLeast(1); val h=(b.height*scale).toInt().coerceAtLeast(1); val page=doc.startPage(PdfDocument.PageInfo.Builder(w,h,i+1).create()); page.canvas.drawBitmap(Bitmap.createScaledBitmap(b,w,h,true),0f,0f,null); doc.finishPage(page) }; doc.writeTo(output); output.flush() } finally { doc.close() } }
}

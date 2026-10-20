import sys, json, zipfile
from pathlib import Path
import cv2
import numpy as np
import pytesseract
from PIL import Image
from PySide6.QtCore import Qt
from PySide6.QtGui import QPixmap, QImage
from PySide6.QtWidgets import QApplication, QMainWindow, QWidget, QLabel, QPushButton, QFileDialog, QVBoxLayout, QHBoxLayout, QListWidget, QTextEdit, QMessageBox, QComboBox

APP_NAME = "Shahab Scanner"

def qimage_from_bgr(img):
    rgb=cv2.cvtColor(img,cv2.COLOR_BGR2RGB); h,w,ch=rgb.shape
    return QImage(rgb.data,w,h,ch*w,QImage.Format.Format_RGB888).copy()

class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__(); self.setWindowTitle(APP_NAME); self.resize(1100,760)
        self.pages=[]; self.current=None
        root=QWidget(); self.setCentralWidget(root); layout=QVBoxLayout(root); bar=QHBoxLayout()
        for label,fn in [("افزودن تصاویر",self.add_images),("چرخش ۹۰°",self.rotate),("خاکستری",self.gray),("بازیابی اصل",self.restore),("OCR",self.ocr),("ذخیره تصویر",self.save_image),("خروجی PDF",self.export_pdf),("ذخیره پروژه",self.save_project),("بازکردن پروژه",self.open_project)]:
            b=QPushButton(label); b.clicked.connect(fn); bar.addWidget(b)
        layout.addLayout(bar); body=QHBoxLayout(); layout.addLayout(body,1)
        self.list=QListWidget(); self.list.currentRowChanged.connect(self.select_page); body.addWidget(self.list,1)
        self.preview=QLabel("برای شروع، تصویر اضافه کنید"); self.preview.setAlignment(Qt.AlignmentFlag.AlignCenter); self.preview.setMinimumSize(400,300); self.preview.setStyleSheet("background:#e5edf4;color:#123")
        body.addWidget(self.preview,3)
        self.text=QTextEdit(); self.text.setPlaceholderText("متن OCR صفحه انتخاب‌شده (قابل ویرایش)"); layout.addWidget(self.text,1)
        self.statusBar().showMessage("آماده")
    def add_images(self):
        paths,_=QFileDialog.getOpenFileNames(self,"انتخاب تصاویر","","Images (*.png *.jpg *.jpeg *.bmp *.tif)")
        for path in paths:
            im=cv2.imread(path)
            if im is not None: self.pages.append({"name":Path(path).name,"original":im.copy(),"image":im.copy(),"ocr":""})
        self.refresh(len(self.pages)-1)
    def refresh(self,row=0):
        self.list.blockSignals(True); self.list.clear()
        for p in self.pages: self.list.addItem(p["name"])
        self.list.blockSignals(False)
        if self.pages: self.list.setCurrentRow(max(0,min(row,len(self.pages)-1)))
        else: self.current=None; self.preview.setText("صفحه‌ای وجود ندارد"); self.preview.setPixmap(QPixmap()); self.text.clear()
    def select_page(self,row):
        if not 0<=row<len(self.pages): self.current=None; return
        self.current=self.pages[row]; self.preview.setPixmap(QPixmap.fromImage(qimage_from_bgr(self.current["image"])).scaled(self.preview.size(),Qt.AspectRatioMode.KeepAspectRatio,Qt.TransformationMode.SmoothTransformation)); self.text.setPlainText(self.current.get("ocr",""))
    def resizeEvent(self,event):
        super().resizeEvent(event)
        if self.current is not None: self.preview.setPixmap(QPixmap.fromImage(qimage_from_bgr(self.current["image"])).scaled(self.preview.size(),Qt.AspectRatioMode.KeepAspectRatio,Qt.TransformationMode.SmoothTransformation))
    def sync_text(self):
        if self.current is not None: self.current["ocr"]=self.text.toPlainText()
    def update_image(self,img):
        if self.current is None: return
        self.sync_text(); self.current["image"]=img; self.select_page(self.list.currentRow())
    def rotate(self):
        if self.current is not None: self.update_image(cv2.rotate(self.current["image"],cv2.ROTATE_90_CLOCKWISE))
    def gray(self):
        if self.current is not None:
            im=cv2.cvtColor(self.current["image"],cv2.COLOR_BGR2GRAY); self.update_image(cv2.cvtColor(im,cv2.COLOR_GRAY2BGR))
    def restore(self):
        if self.current is not None: self.update_image(self.current["original"].copy())
    def save_image(self):
        if self.current is None: return
        path,_=QFileDialog.getSaveFileName(self,"ذخیره تصویر","scan.png","PNG (*.png);;JPEG (*.jpg)")
        if path: cv2.imwrite(path,self.current["image"]); self.statusBar().showMessage("تصویر ذخیره شد")
    def export_pdf(self):
        if not self.pages: return
        path,_=QFileDialog.getSaveFileName(self,"خروجی PDF","document.pdf","PDF (*.pdf)")
        if not path:return
        images=[Image.fromarray(cv2.cvtColor(p["image"],cv2.COLOR_BGR2RGB)).convert("RGB") for p in self.pages]
        try: images[0].save(path,save_all=True,append_images=images[1:]); self.statusBar().showMessage("PDF ذخیره شد")
        except Exception as e: QMessageBox.critical(self,"خطا",str(e))
    def ocr(self):
        if self.current is None:return
        from PySide6.QtWidgets import QInputDialog
        language,accepted=QInputDialog.getItem(self,"زبان OCR","زبان نصب‌شده در Tesseract:",["fas","eng","fas+eng"],0,False)
        if not accepted:return
        try:
            text=pytesseract.image_to_string(cv2.cvtColor(self.current["image"],cv2.COLOR_BGR2GRAY),lang=language)
            self.text.setPlainText(text); self.sync_text(); self.statusBar().showMessage("OCR انجام شد؛ نتیجه را بازبینی کنید")
        except Exception as e: QMessageBox.warning(self,"خطای OCR","Tesseract و زبان انتخابی باید نصب باشند.\n"+str(e))
    def save_project(self):
        if not self.pages:return
        path,_=QFileDialog.getSaveFileName(self,"ذخیره پروژه","project.shahab","Shahab Project (*.shahab)")
        if not path:return
        self.sync_text()
        try:
            with zipfile.ZipFile(path,"w",zipfile.ZIP_DEFLATED) as z:
                manifest={"format":"ShahabScannerProject","version":1,"pages":[]}
                for i,p in enumerate(self.pages):
                    a=f"pages/{i:04d}_original.png"; b=f"pages/{i:04d}_edited.png"
                    for key,name in [("original",a),("image",b)]:
                        ok,enc=cv2.imencode(".png",p[key])
                        if not ok: raise ValueError("رمزگذاری تصویر ناموفق بود")
                        z.writestr(name,enc.tobytes())
                    manifest["pages"].append({"name":p["name"],"original":a,"edited":b,"ocr":p.get("ocr","")})
                z.writestr("project.json",json.dumps(manifest,ensure_ascii=False,indent=2))
            self.statusBar().showMessage("پروژه ذخیره شد")
        except Exception as e: QMessageBox.critical(self,"خطا",str(e))
    def open_project(self):
        path,_=QFileDialog.getOpenFileName(self,"بازکردن پروژه","","Shahab Project (*.shahab *.zip)")
        if not path:return
        try:
            loaded=[]
            with zipfile.ZipFile(path) as z:
                m=json.loads(z.read("project.json"))
                if m.get("format")!="ShahabScannerProject": raise ValueError("فرمت پروژه معتبر نیست")
                for p in m["pages"]:
                    orig=cv2.imdecode(np.frombuffer(z.read(p["original"]),np.uint8),cv2.IMREAD_COLOR)
                    edit=cv2.imdecode(np.frombuffer(z.read(p["edited"]),np.uint8),cv2.IMREAD_COLOR)
                    if orig is None or edit is None: raise ValueError("تصویر پروژه خراب است")
                    loaded.append({"name":p.get("name","Page"),"original":orig,"image":edit,"ocr":p.get("ocr","")})
            self.pages=loaded; self.refresh(0); self.statusBar().showMessage(f"پروژه باز شد: {len(loaded)} صفحه")
        except Exception as e: QMessageBox.critical(self,"خطا",str(e))

if __name__=="__main__":
    app=QApplication(sys.argv); win=MainWindow(); win.show(); sys.exit(app.exec())

# دليل المستخدم وإدارة برنامج Watcher (User Manual)

---

## 1. نبذة عن البرنامج
برنامج **Watcher** يعمل في الخلفية بشكل خفي تماماً وبدون أي واجهة مستخدم على سطح مكتب الأولاد. يقوم دورياً بالتقاط صور الشاشة وحفظها مؤقتاً في طابور محلي مشفر، ثم رفعها إلى مجلدك الخاص في Google Drive، مع حذف الصور محلياً بعد نجاح الرفع.

---

## 2. متطلبات الأمان الهامة لحماية البرنامج من العبث
لكي تكون طبقة الحماية فعالة 100%:
1. **نوع حساب الطفل**: يجب أن يكون حساب الطفل على نظام التشغيل حساب **عادي (Standard User)** وليس مسؤولاً (Administrator / sudoer).
2. **حساب الأب**: فقط حساب الأب هو المسؤول وله صلاحية تعديل ملفات النظام.

---

## 3. التثبيت على الأجهزة

### على Windows:
1. انسخ ملف `syshealthd.jar` وملف `credentials.json` إلى الجهاز.
2. انقر بالزر الأيمن على `scripts\install-windows.bat` واختر **Run as Administrator** (تشغيل كمسؤول).
3. سيبدأ البرنامج بالعمل تلقائياً فور تسجيل دخول أي مستخدم.

### على macOS:
1. افتح Terminal وتوجه لمجلد المشروع.
2. شغّل سكربت التثبيت بصلاحيات root:
   ```bash
   sudo ./scripts/install-mac.sh
   ```
3. **خطوة هامة جداً على macOS**:
   - اذهب إلى **System Settings (إعدادات النظام) -> Privacy & Security (الخصوصية والأمان) -> Screen Recording (تسجيل الشاشة)**.
   - اسمح لـ Java بالتقاط الشاشة.

### على Linux:
1. افتح Terminal وشغّل:
   ```bash
   sudo ./scripts/install-linux.sh
   ```

---

## 4. أداة التحكم والإدارة للأب (Admin CLI)

البرنامج لا يفتح أي نافذة. التحكم والإعدادات تتم فقط من سطر الأوامر باستخدام كلمة مرور الأب:

### عرض حالة البرنامج وطابور الصور:
```bash
java -jar syshealthd.jar --admin --password "كلمة_المرور" --status
```

### تغيير الفاصل الزمني للالتقاط (مثلاً كل 3 دقائق):
```bash
java -jar syshealthd.jar --admin --password "كلمة_المرور" --set-interval 3
```

### تغيير جودة ضغط الصور (من 0.1 إلى 1.0):
```bash
java -jar syshealthd.jar --admin --password "كلمة_المرور" --set-quality 0.5
```

### تغيير مدة الاحتفاظ بالصور على Google Drive (مثلاً 15 يوم):
```bash
java -jar syshealthd.jar --admin --password "كلمة_المرور" --set-retention 15
```

### تغيير كلمة مرور الإدارة:
```bash
java -jar syshealthd.jar --admin --password "القديمة" --change-password "الجديدة"
```

### إزالة البرنامج بالكامل:
```bash
java -jar syshealthd.jar --admin --password "كلمة_المرور" --uninstall
```

package com.dentalpro.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.View;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.widget.ProgressBar;
import android.app.DownloadManager;
import android.net.Uri;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.webkit.PermissionRequest;
import android.webkit.GeolocationPermissions;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.app.AlertDialog;
import android.net.Uri;
import android.webkit.ValueCallback;
import android.provider.MediaStore;
import android.os.Environment;
import android.content.ContentValues;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.biometric.BiometricPrompt;
import androidx.biometric.BiometricManager;
import java.util.concurrent.Executor;

public class MainActivity extends androidx.appcompat.app.AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private String fcmTokenForWebView = "";
    private String pendingGeoOrigin;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private static final String WEBSITE_URL = "https://startling-vacherin-6fff8e.netlify.app/";
    private SwipeRefreshLayout swipeRefresh;
    private ValueCallback<Uri[]> fileUploadCallback;
    private Uri cameraImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set system bar colors
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int systemBarColor = Color.parseColor("#4F46E5");
            getWindow().setStatusBarColor(systemBarColor);
            getWindow().setNavigationBarColor(systemBarColor);
        }
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeColors(Color.parseColor("#6366F1"));
        swipeRefresh.setOnRefreshListener(() -> {
            webView.reload();
        });
        checkAndShowRateDialog(5);
        
        setupWebView();
        
        
        // Request runtime permissions
        java.util.List<String> permissionsNeeded = new java.util.ArrayList<>();
        String[] requiredPerms = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR, Manifest.permission.USE_BIOMETRIC, Manifest.permission.READ_EXTERNAL_STORAGE};
        for (String perm : requiredPerms) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(perm);
            }
        }
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), 2001);
        }
        
        authenticateUser();
        
        // Handle deep link intent
        handleIntent(getIntent());
        
        // Load directly; ConnectivityManager can be unreliable on some devices/VPNs.
        // WebView will show its own error page if the connection is actually unavailable.
        webView.loadUrl(WEBSITE_URL);
    }

    private void checkAndShowRateDialog(int targetLaunches) {
        android.content.SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean ratedAlready = prefs.getBoolean("rated", false);
        if (ratedAlready) return;
        int launches = prefs.getInt("launch_count", 0) + 1;
        prefs.edit().putInt("launch_count", launches).apply();
        if (launches == targetLaunches) {
            new AlertDialog.Builder(this)
                .setTitle("Enjoying the app?")
                .setMessage("If you like the app, please take a moment to rate it. It won't take more than a minute. Thanks!")
                .setPositiveButton("Rate Now", (d, w) -> {
                    prefs.edit().putBoolean("rated", true).apply();
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName())));
                    } catch (android.content.ActivityNotFoundException e) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
                    }
                })
                .setNeutralButton("Remind Later", null)
                .setNegativeButton("No Thanks", (d, w) -> prefs.edit().putBoolean("rated", true).apply())
                .show();
        }
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(true);
        webSettings.setDefaultTextEncodingName("utf-8");
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setDatabaseEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);
        webSettings.setGeolocationEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                // Custom JavaScript injection
                view.evaluateJavascript("// LocalStorage Data Initialization let hospitals = JSON.parse(localStorage.getItem(\'dental_hospitals\')) || [     { id: 1, name: \'City Central Dental Clinic\', location: \'Bole Road, Addis Ababa\', schedule: \'Mon, Wed (Morning)\' },     { id: 2, name: \'St. Gabriel General Hospital\', location: \'Megenagna, Addis Ababa\', schedule: \'Tue, Thu (Afternoon)\' },     { id: 3, name: \'Apex Specialized Medical Center\', location: \'Kazanchis, Addis Ababa\', schedule: \'Sat (Full Day)\' } ];  let patients = JSON.parse(localStorage.getItem(\'dental_patients\')) || [     { id: 1, name: \'Abebe Bekele\', phone: \'+251 911 223344\', hospitalId: 1, notes: \'Allergic to penicillin.\' },     { id: 2, name: \'Tigist Alemayehu\', phone: \'+251 922 556677\', hospitalId: 2, notes: \'Root canal follow-up.\' },     { id: 3, name: \'Dawit Yohannes\', phone: \'+251 933 889900\', hospitalId: 1, notes: \'Routine checkup.\' } ];  // Appointments: Purely schedule and procedure plan (NO payment fields) let appointments = JSON.parse(localStorage.getItem(\'dental_appointments\')) || [     { id: 1, patientId: 1, hospitalId: 1, datetime: \'2026-10-18T09:30\', procedure: \'Root Canal Consultation\' },     { id: 2, patientId: 2, hospitalId: 2, datetime: \'2026-10-19T14:00\', procedure: \'Extraction Surgery Prep\' } ];  // Payments / Invoices: Standalone financial ledger supporting installments (Paid so far vs Total Fee) let payments = JSON.parse(localStorage.getItem(\'dental_payments\')) || [     { id: 1, patientId: 1, hospitalId: 1, date: \'2026-10-15\', procedure: \'Root Canal Stage 1\', totalFee: 150.00, paidSoFar: 50.00, payer: \'Customer\' },     { id: 2, patientId: 3, hospitalId: 1, date: \'2026-10-14\', procedure: \'Scaling & Polishing\', totalFee: 60.00, paidSoFar: 60.00, payer: \'Customer\' } ];  let currentGlobalHospitalFilter = \'all\';  function saveData() {     localStorage.setItem(\'dental_hospitals\', JSON.stringify(hospitals));     localStorage.setItem(\'dental_patients\', JSON.stringify(patients));     localStorage.setItem(\'dental_appointments\', JSON.stringify(appointments));     localStorage.setItem(\'dental_payments\', JSON.stringify(payments));     renderAll(); }  document.addEventListener(\'DOMContentLoaded\', () => {     const options = { weekday: \'long\', year: \'numeric\', month: \'long\', day: \'numeric\' };     document.getElementById(\'current-date\').innerText = new Date().toLocaleDateString(\'en-US\', options);      // Sidebar navigation     document.querySelectorAll(\'.sidebar-nav .nav-item\').forEach(item => {         item.addEventListener(\'click\', (e) => {             e.preventDefault();             document.querySelectorAll(\'.sidebar-nav .nav-item\').forEach(n => n.classList.remove(\'active\'));             document.querySelectorAll(\'.tab-section\').forEach(s => s.classList.remove(\'active\'));                          item.classList.add(\'active\');             const tabId = item.getAttribute(\'data-tab\') + \'-tab\';             document.getElementById(tabId).classList.add(\'active\');                          const titles = {                 \'dashboard\': \'Dashboard Overview\',                 \'appointments\': \'Appointments & Schedule\',                 \'finances\': \'Financial Ledger & Installments\',                 \'patients\': \'Patient Directory\',                 \'hospitals\': \'Partner Hospitals\'             };             document.getElementById(\'page-title\').innerText = titles[item.getAttribute(\'data-tab\')];         });     });      document.getElementById(\'global-hospital-filter\').addEventListener(\'change\', (e) => {         currentGlobalHospitalFilter = e.target.value;         renderAll();     });      // Appt Filters & Search     document.getElementById(\'appt-time-filter\').addEventListener(\'change\', handleApptFilterChange);     document.getElementById(\'appt-start-date\').addEventListener(\'change\', renderAppointments);     document.getElementById(\'appt-end-date\').addEventListener(\'change\', renderAppointments);     document.getElementById(\'search-appointments\').addEventListener(\'input\', renderAppointments);      // Finance Filters     document.getElementById(\'fin-time-filter\').addEventListener(\'change\', handleFinFilterChange);     document.getElementById(\'fin-start-date\').addEventListener(\'change\', renderFinances);     document.getElementById(\'fin-end-date\').addEventListener(\'change\', renderFinances);     document.getElementById(\'fin-status-filter\').addEventListener(\'change\', renderFinances);     document.getElementById(\'fin-payer-filter\').addEventListener(\'change\', renderFinances);      // Patient filters     document.getElementById(\'patient-clinic-filter\').addEventListener(\'change\', renderPatients);     document.getElementById(\'search-patients\').addEventListener(\'input\', renderPatients);      // Form submits     document.getElementById(\'appointment-form\').addEventListener(\'submit\', handleAppointmentSubmit);     document.getElementById(\'payment-form\').addEventListener(\'submit\', handlePaymentSubmit);     document.getElementById(\'patient-form\').addEventListener(\'submit\', handlePatientSubmit);     document.getElementById(\'hospital-form\').addEventListener(\'submit\', handleHospitalSubmit);      renderAll(); });  function switchTab(tabName) {     document.querySelector(`.sidebar-nav .nav-item[data-tab=\"${tabName}\"]`).click(); }  function handleApptFilterChange(e) {     const val = e.target.value;     document.getElementById(\'appt-custom-date-container\').style.display = (val === \'custom\') ? \'flex\' : \'none\';     renderAppointments(); }  function handleFinFilterChange(e) {     const val = e.target.value;     document.getElementById(\'fin-custom-date-container\').style.display = (val === \'custom\') ? \'flex\' : \'none\';     renderFinances(); }  function isDateInFilter(dateTimeStr, filterType, startDateVal, endDateVal) {     if (filterType === \'all\') return true;     const apptDate = new Date(dateTimeStr);     const today = new Date();      if (filterType === \'day\') {         return apptDate.toDateString() === today.toDateString();     } else if (filterType === \'week\') {         const startOfWeek = new Date(today);         startOfWeek.setDate(today.getDate() - today.getDay());         startOfWeek.setHours(0,0,0,0);         const endOfWeek = new Date(startOfWeek);         endOfWeek.setDate(startOfWeek.getDate() + 6);         endOfWeek.setHours(23,59,59,999);         return apptDate >= startOfWeek && apptDate <= endOfWeek;     } else if (filterType === \'month\') {         return apptDate.getMonth() === today.getMonth() && apptDate.getFullYear() === today.getFullYear();     } else if (filterType === \'custom\') {         if (!startDateVal || !endDateVal) return true;         const start = new Date(startDateVal);         const end = new Date(endDateVal);         end.setHours(23,59,59,999);         return apptDate >= start && apptDate <= end;     }     return true; }  function renderAll() {     populateDropdowns();     renderDashboard();     renderAppointments();     renderFinances();     renderPatients();     renderHospitals(); }  function populateDropdowns() {     const globalFilter = document.getElementById(\'global-hospital-filter\');     const prevVal = globalFilter.value;     globalFilter.innerHTML = \'<option value=\"all\">All Hospitals</option>\' +          hospitals.map(h => `<option value=\"${h.id}\">${h.name}</option>`).join(\'\');     globalFilter.value = prevVal;      const patClinicFilter = document.getElementById(\'patient-clinic-filter\');     const prevPatClinic = patClinicFilter.value;     patClinicFilter.innerHTML = \'<option value=\"all\">All Clinics / Hospitals</option>\' +          hospitals.map(h => `<option value=\"${h.id}\">${h.name}</option>`).join(\'\');     patClinicFilter.value = prevPatClinic;      // Dropdown population for modals     const patientOpts = patients.map(p => `<option value=\"${p.id}\">${p.name} (${p.phone})</option>`).join(\'\');     const hospitalOpts = hospitals.map(h => `<option value=\"${h.id}\">${h.name}</option>`).join(\'\');      document.getElementById(\'appt-patient\').innerHTML = patientOpts;     document.getElementById(\'appt-hospital\').innerHTML = hospitalOpts;     document.getElementById(\'pay-patient\').innerHTML = patientOpts;     document.getElementById(\'pay-hospital\').innerHTML = hospitalOpts;     document.getElementById(\'pat-hospital\').innerHTML = hospitalOpts; }  function filterByHospital(list) {     if (currentGlobalHospitalFilter === \'all\') return list;     return list.filter(item => String(item.hospitalId) === String(currentGlobalHospitalFilter)); }  /* Dashboard */ function renderDashboard() {     const filteredAppts = filterByHospital(appointments);     const filteredPayments = filterByHospital(payments);      let totalEarned = 0;     let pendingAmount = 0;      filteredPayments.forEach(pay => {         totalEarned += Number(pay.paidSoFar);         const due = Number(pay.totalFee) - Number(pay.paidSoFar);         if (due > 0) pendingAmount += due;     });      document.getElementById(\'stat-total-earned\').innerText = `$${totalEarned.toFixed(2)}`;     document.getElementById(\'stat-pending\').innerText = `$${pendingAmount.toFixed(2)}`;     document.getElementById(\'stat-appointments\').innerText = filteredAppts.length;     document.getElementById(\'stat-patients\').innerText = patients.length;      // Upcoming visits     const todayTableBody = document.querySelector(\'#todays-appointments-table tbody\');     if (filteredAppts.length === 0) {         todayTableBody.innerHTML = `<tr><td colspan=\"4\" style=\"text-align:center; color: var(--gray-600);\">No appointments scheduled.</td></tr>`;     } else {         todayTableBody.innerHTML = filteredAppts.slice(0, 5).map(app => {             const patient = patients.find(p => p.id == app.patientId) || { name: \'Unknown\' };             const hospital = hospitals.find(h => h.id == app.hospitalId) || { name: \'Unknown\' };             return `                 <tr>                     <td><strong>${app.datetime.replace(\'T\', \' \')}</strong></td>                     <td>${patient.name}</td>                     <td>${hospital.name}</td>                     <td>${app.procedure}</td>                 </tr>             `;         }).join(\'\');     }      // Outstanding balances table     const unpaidList = filteredPayments.filter(pay => Number(pay.paidSoFar) < Number(pay.totalFee));     const unpaidTableBody = document.querySelector(\'#dashboard-unpaid-table tbody\');     if (unpaidList.length === 0) {         unpaidTableBody.innerHTML = `<tr><td colspan=\"5\" style=\"text-align:center; color: var(--gray-600);\">All balances fully settled!</td></tr>`;     } else {         unpaidTableBody.innerHTML = unpaidList.slice(0, 5).map(pay => {             const patient = patients.find(p => p.id == pay.patientId) || { name: \'Unknown\' };             const hospital = hospitals.find(h => h.id == pay.hospitalId) || { name: \'Unknown\' };             const due = Number(pay.totalFee) - Number(pay.paidSoFar);             return `                 <tr>                     <td>${patient.name}</td>                     <td>${hospital.name}</td>                     <td><strong>$${due.toFixed(2)}</strong></td>                     <td><span class=\"badge danger\">${pay.payer}</span></td>                     <td><button class=\"btn btn-primary\" style=\"padding: 2px 8px; font-size: 11px;\" onclick=\"openPaymentModal(${pay.id})\">Add Installment</button></td>                 </tr>             `;         }).join(\'\');     } }  /* Appointments */ function renderAppointments() {     const searchVal = document.getElementById(\'search-appointments\').value.toLowerCase();     const timeFilter = document.getElementById(\'appt-time-filter\').value;     const startVal = document.getElementById(\'appt-start-date\').value;     const endVal = document.getElementById(\'appt-end-date\').value;      let filtered = filterByHospital(appointments);     filtered = filtered.filter(app => isDateInFilter(app.datetime, timeFilter, startVal, endVal));      if (searchVal) {         filtered = filtered.filter(app => {             const patient = patients.find(p => p.id == app.patientId);             const hospital = hospitals.find(h => h.id == app.hospitalId);             return (patient && patient.name.toLowerCase().includes(searchVal)) ||                     app.procedure.toLowerCase().includes(searchVal) ||                    (hospital && hospital.name.toLowerCase().includes(searchVal));         });     }      const tbody = document.querySelector(\'#full-appointments-table tbody\');     if (filtered.length === 0) {         tbody.innerHTML = `<tr><td colspan=\"5\" style=\"text-align:center; color: var(--gray-600);\">No appointments found.</td></tr>`;         return;     }      tbody.innerHTML = filtered.map(app => {         const patient = patients.find(p => p.id == app.patientId) || { name: \'Unknown\' };         const hospital = hospitals.find(h => h.id == app.hospitalId) || { name: \'Unknown\' };         return `             <tr>                 <td>${app.datetime.replace(\'T\', \' \')}</td>                 <td><strong>${patient.name}</strong></td>                 <td>${hospital.name}</td>                 <td>${app.procedure}</td>                 <td>                     <button class=\"action-icon-btn\" onclick=\"openAppointmentModal(${app.id})\" title=\"Edit\"><i class=\"fa-solid fa-pen\"></i></button>                     <button class=\"action-icon-btn delete\" onclick=\"deleteAppointment(${app.id})\" title=\"Delete\"><i class=\"fa-solid fa-trash\"></i></button>                 </td>             </tr>         `;     }).join(\'\'); }  /* Finances & Installments */ function renderFinances() {     const timeFilter = document.getElementById(\'fin-time-filter\').value;     const startVal = document.getElementById(\'fin-start-date\').value;     const endVal = document.getElementById(\'fin-end-date\').value;     const statusFilter = document.getElementById(\'fin-status-filter\').value;     const payerFilter = document.getElementById(\'fin-payer-filter\').value;      let filtered = filterByHospital(payments);     filtered = filtered.filter(pay => isDateInFilter(pay.date, timeFilter, startVal, endVal));      // Summary Calculations     let paidTotal = 0;     let unpaidCust = 0;     let unpaidClinic = 0;      payments.forEach(pay => {         paidTotal += Number(pay.paidSoFar);         const due = Number(pay.totalFee) - Number(pay.paidSoFar);         if (due > 0) {             if (pay.payer === \'Customer\') unpaidCust += due;             else unpaidClinic += due;         }     });      document.getElementById(\'fin-paid-total\').innerText = `$${paidTotal.toFixed(2)}`;     document.getElementById(\'fin-unpaid-customer\').innerText = `$${unpaidCust.toFixed(2)}`;     document.getElementById(\'fin-unpaid-clinic\').innerText = `$${unpaidClinic.toFixed(2)}`;      // Status filter     if (statusFilter === \'Paid\') {         filtered = filtered.filter(p => Number(p.paidSoFar) >= Number(p.totalFee));     } else if (statusFilter === \'Unpaid\') {         filtered = filtered.filter(p => Number(p.paidSoFar) < Number(p.totalFee));     }      if (payerFilter !== \'all\') {         filtered = filtered.filter(p => p.payer === payerFilter);     }      const tbody = document.querySelector(\'#finances-table tbody\');     if (filtered.length === 0) {         tbody.innerHTML = `<tr><td colspan=\"9\" style=\"text-align:center; color: var(--gray-600);\">No payment records found. Click \'Record Procedure Payment\' to add one.</td></tr>`;         return;     }      tbody.innerHTML = filtered.map(pay => {         const patient = patients.find(p => p.id == pay.patientId) || { name: \'Unknown\' };         const hospital = hospitals.find(h => h.id == pay.hospitalId) || { name: \'Unknown\' };         const total = Number(pay.totalFee);         const paidSoFar = Number(pay.paidSoFar);         const due = total - paidSoFar;         const isFullyPaid = paidSoFar >= total;          return `             <tr>                 <td>${pay.date}</td>                 <td><strong>${patient.name}</strong></td>                 <td>${hospital.name}</td>                 <td>${pay.procedure}</td>                 <td>$${total.toFixed(2)}</td>                 <td class=\"text-success\"><strong>$${paidSoFar.toFixed(2)}</strong></td>                 <td class=\"${due > 0 ? \'text-warning\' : \'\'}\"><strong>$${due.toFixed(2)}</strong></td>                 <td><span class=\"badge ${isFullyPaid ? \'success\' : \'danger\'}\">${isFullyPaid ? \'Fully Paid\' : \'Installment Due\'}</span></td>                 <td>                     <button class=\"action-icon-btn\" onclick=\"openPaymentModal(${pay.id})\" title=\"Edit / Add Installment\"><i class=\"fa-solid fa-pen-to-square\"></i></button>                     <button class=\"action-icon-btn delete\" onclick=\"deletePayment(${pay.id})\" title=\"Delete\"><i class=\"fa-solid fa-trash\"></i></button>                 </td>             </tr>         `;     }).join(\'\'); }  /* Patients */ function renderPatients() {     const searchVal = document.getElementById(\'search-patients\').value.toLowerCase();     const clinicFilter = document.getElementById(\'patient-clinic-filter\').value;      let filtered = patients;     if (clinicFilter !== \'all\') filtered = filtered.filter(p => String(p.hospitalId) === String(clinicFilter));     if (searchVal) filtered = filtered.filter(p => p.name.toLowerCase().includes(searchVal) || p.phone.toLowerCase().includes(searchVal));      const tbody = document.querySelector(\'#patients-table tbody\');     if (filtered.length === 0) {         tbody.innerHTML = `<tr><td colspan=\"6\" style=\"text-align:center; color: var(--gray-600);\">No patients found.</td></tr>`;         return;     }      tbody.innerHTML = filtered.map(p => {         const hospital = hospitals.find(h => h.id == p.hospitalId) || { name: \'Unassigned\' };         const visitCount = appointments.filter(a => a.patientId == p.id).length;         return `             <tr>                 <td><strong>${p.name}</strong></td>                 <td>${p.phone}</td>                 <td><span class=\"badge info\"><i class=\"fa-solid fa-hospital\"></i> ${hospital.name}</span></td>                 <td>${p.notes || \'None\'}</td>                 <td><span class=\"badge primary\">${visitCount} Visits</span></td>                 <td>                     <button class=\"action-icon-btn delete\" onclick=\"deletePatient(${p.id})\" title=\"Delete\"><i class=\"fa-solid fa-trash\"></i></button>                 </td>             </tr>         `;     }).join(\'\'); }  /* Hospitals */ function renderHospitals() {     const container = document.getElementById(\'hospitals-grid-container\');     container.innerHTML = hospitals.map(h => {         const hospPayments = payments.filter(p => p.hospitalId == h.id);         const hospEarnings = hospPayments.reduce((sum, p) => sum + Number(p.paidSoFar), 0);         const patientCount = patients.filter(p => p.hospitalId == h.id).length;          return `             <div class=\"hospital-card\">                 <h4><i class=\"fa-solid fa-hospital\"></i> ${h.name}</h4>                 <p><i class=\"fa-solid fa-location-dot\"></i> ${h.location}</p>                 <p><i class=\"fa-solid fa-clock\"></i> ${h.schedule || \'Flexible\'}</p>                 <div style=\"margin-top: 12px; display: flex; justify-content: space-between; align-items: center; border-top: 1px solid var(--gray-200); padding-top: 10px;\">                     <span style=\"font-size: 11px; color: var(--gray-600);\">Patients: <strong>${patientCount}</strong> | Revenue: <strong>$${hospEarnings.toFixed(2)}</strong></span>                     <button class=\"action-icon-btn delete\" onclick=\"deleteHospital(${h.id})\" title=\"Delete\"><i class=\"fa-solid fa-trash\"></i></button>                 </div>             </div>         `;     }).join(\'\'); }  /* Modals & Handlers */ function openAppointmentModal(appId = null) {     document.getElementById(\'appointment-modal\').classList.add(\'active\');     document.getElementById(\'appointment-form\').reset();     document.getElementById(\'appt-id\').value = \'\';     document.getElementById(\'appt-modal-title\').innerText = \'Schedule Appointment\';      if (appId) {         const app = appointments.find(a => a.id === appId);         if (app) {             document.getElementById(\'appt-modal-title\').innerText = \'Edit Appointment\';             document.getElementById(\'appt-id\').value = app.id;             document.getElementById(\'appt-patient\').value = app.patientId;             document.getElementById(\'appt-hospital\').value = app.hospitalId;             document.getElementById(\'appt-datetime\').value = app.datetime;             document.getElementById(\'appt-procedure\').value = app.procedure;         }     } else {         const now = new Date();         now.setMinutes(now.getMinutes() - now.getTimezoneOffset());         document.getElementById(\'appt-datetime\').value = now.toISOString().slice(0, 16);     } }  function handleAppointmentSubmit(e) {     e.preventDefault();     const id = document.getElementById(\'appt-id\').value;     const data = {         patientId: Number(document.getElementById(\'appt-patient\').value),         hospitalId: Number(document.getElementById(\'appt-hospital\').value),         datetime: document.getElementById(\'appt-datetime\').value,         procedure: document.getElementById(\'appt-procedure\').value     };      if (id) {         const index = appointments.findIndex(a => a.id == id);         if (index !== -1) appointments[index] = { id: Number(id), ...data };     } else {         const newId = appointments.length > 0 ? Math.max(...appointments.map(a => a.id)) + 1 : 1;         appointments.push({ id: newId, ...data });     }      closeModals();     saveData(); }  function deleteAppointment(id) {     if (confirm(\'Delete this appointment?\')) {         appointments = appointments.filter(a => a.id !== id);         saveData();     } }  function openPaymentModal(payId = null) {     document.getElementById(\'payment-modal\').classList.add(\'active\');     document.getElementById(\'payment-form\').reset();     document.getElementById(\'pay-id\').value = \'\';     document.getElementById(\'pay-modal-title\').innerText = \'Record Procedure Payment / Installment\';      if (payId) {         const pay = payments.find(p => p.id === payId);         if (pay) {             document.getElementById(\'pay-modal-title\').innerText = \'Update Installment / Payment\';             document.getElementById(\'pay-id\').value = pay.id;             document.getElementById(\'pay-patient\').value = pay.patientId;             document.getElementById(\'pay-hospital\').value = pay.hospitalId;             document.getElementById(\'pay-date\').value = pay.date;             document.getElementById(\'pay-procedure\').value = pay.procedure;             document.getElementById(\'pay-total-fee\').value = pay.totalFee;             document.getElementById(\'pay-current-amount\').value = pay.paidSoFar; // Show current total paid for easy editing/adding             document.getElementById(\'pay-payer\').value = pay.payer;         }     } else {         document.getElementById(\'pay-date\').value = new Date().toISOString().split(\'T\')[0];     } }  function handlePaymentSubmit(e) {     e.preventDefault();     const id = document.getElementById(\'pay-id\').value;     const data = {         patientId: Number(document.getElementById(\'pay-patient\').value),         hospitalId: Number(document.getElementById(\'pay-hospital\').value),         date: document.getElementById(\'pay-date\').value,         procedure: document.getElementById(\'pay-procedure\').value,         totalFee: parseFloat(document.getElementById(\'pay-total-fee\').value),         paidSoFar: parseFloat(document.getElementById(\'pay-current-amount\').value),         payer: document.getElementById(\'pay-payer\').value     };      if (id) {         const index = payments.findIndex(p => p.id == id);         if (index !== -1) payments[index] = { id: Number(id), ...data };     } else {         const newId = payments.length > 0 ? Math.max(...payments.map(p => p.id)) + 1 : 1;         payments.push({ id: newId, ...data });     }      closeModals();     saveData(); }  function deletePayment(id) {     if (confirm(\'Delete this payment record?\')) {         payments = payments.filter(p => p.id !== id);         saveData();     } }  function openPatientModal() {     document.getElementById(\'patient-modal\').classList.add(\'active\');     document.getElementById(\'patient-form\').reset(); }  function handlePatientSubmit(e) {     e.preventDefault();     const newPatient = {         id: patients.length > 0 ? Math.max(...patients.map(p => p.id)) + 1 : 1,         name: document.getElementById(\'pat-name\').value,         phone: document.getElementById(\'pat-phone\').value,         hospitalId: Number(document.getElementById(\'pat-hospital\').value),         notes: document.getElementById(\'pat-notes\').value     };     patients.push(newPatient);     closeModals();     saveData(); }  function deletePatient(id) {     if (confirm(\'Delete patient and their history?\')) {         patients = patients.filter(p => p.id !== id);         appointments = appointments.filter(a => a.patientId !== id);         payments = payments.filter(p => p.patientId !== id);         saveData();     } }  function openHospitalModal() {     document.getElementById(\'hospital-modal\').classList.add(\'active\');     document.getElementById(\'hospital-form\').reset(); }  function handleHospitalSubmit(e) {     e.preventDefault();     const newHospital = {         id: hospitals.length > 0 ? Math.max(...hospitals.map(h => h.id)) + 1 : 1,         name: document.getElementById(\'hosp-name\').value,         location: document.getElementById(\'hosp-location\').value,         schedule: document.getElementById(\'hosp-schedule\').value     };     hospitals.push(newHospital);     closeModals();     saveData(); }  function deleteHospital(id) {     if (confirm(\'Delete hospital?\')) {         hospitals = hospitals.filter(h => h.id !== id);         saveData();     } }  function closeModals() {     document.querySelectorAll(\'.modal\').forEach(m => m.classList.remove(\'active\')); }", null);
                // Custom CSS injection via Base64 to avoid quote escaping issues
                String cssStr = ":root {     --primary: #0ea5e9;     --primary-dark: #0284c7;     --primary-light: #e0f2fe;     --success: #10b981;     --warning: #f59e0b;     --danger: #ef4444;     --dark: #0f172a;     --gray-100: #f1f5f9;     --gray-200: #e2e8f0;     --gray-600: #475569;     --gray-900: #0f172a;     --bg-main: #f8fafc;     --white: #ffffff;     --shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -2px rgba(0, 0, 0, 0.1);     --radius: 12px; }  * {     margin: 0;     padding: 0;     box-sizing: border-box;     font-family: \'Plus Jakarta Sans\', sans-serif; }  body {     background-color: var(--bg-main);     color: var(--gray-900); }  .app-container {     display: flex;     height: 100vh;     overflow: hidden; }  /* Sidebar */ .sidebar {     width: 260px;     background-color: var(--white);     border-right: 1px solid var(--gray-200);     display: flex;     flex-direction: column;     justify-content: space-between; }  .sidebar-brand {     padding: 24px;     display: flex;     align-items: center;     gap: 12px; }  .logo-icon {     font-size: 24px;     color: var(--primary);     background: var(--primary-light);     padding: 10px;     border-radius: 10px; }  .sidebar-brand h2 { font-size: 20px; font-weight: 700; } .sidebar-brand span { color: var(--primary); }  .sidebar-nav {     display: flex;     flex-direction: column;     gap: 4px;     padding: 0 16px;     flex: 1; }  .nav-item {     display: flex;     align-items: center;     gap: 14px;     padding: 12px 16px;     color: var(--gray-600);     text-decoration: none;     font-weight: 500;     border-radius: var(--radius);     transition: all 0.2s ease; }  .nav-item:hover, .nav-item.active {     background-color: var(--primary-light);     color: var(--primary-dark); }  .sidebar-footer {     padding: 20px;     border-top: 1px solid var(--gray-200); }  .doctor-badge {     display: flex;     align-items: center;     gap: 12px; }  .doctor-badge i {     font-size: 28px;     color: var(--primary);     background: var(--primary-light);     padding: 8px;     border-radius: 50%; }  .doc-name { display: block; font-weight: 700; font-size: 14px; } .doc-specialty { font-size: 12px; color: var(--gray-600); }  /* Main Area */ .main-content {     flex: 1;     display: flex;     flex-direction: column;     overflow: hidden; }  .top-header {     background: var(--white);     padding: 16px 32px;     border-bottom: 1px solid var(--gray-200);     display: flex;     justify-content: space-between;     align-items: center; }  .header-title h1 { font-size: 22px; font-weight: 700; } .header-title p { font-size: 13px; color: var(--gray-600); }  .header-actions {     display: flex;     align-items: center;     gap: 16px; }  .filter-wrapper, .filter-group {     display: flex;     align-items: center;     gap: 8px;     background: var(--white);     border: 1px solid var(--gray-200);     padding: 6px 12px;     border-radius: 8px;     font-size: 13px;     font-weight: 600; }  .filter-wrapper select, .filter-group select, .filter-group input {     border: none;     background: transparent;     font-weight: 600;     color: var(--gray-900);     outline: none;     cursor: pointer; }  .filter-toolbar {     display: flex;     flex-wrap: wrap;     align-items: center;     gap: 12px;     margin-bottom: 20px; }  .custom-date-inputs {     display: flex;     align-items: center;     gap: 8px;     background: var(--white);     border: 1px solid var(--gray-200);     padding: 6px 12px;     border-radius: 8px;     font-size: 13px; }  .custom-date-inputs input { border: none; outline: none; font-size: 13px; }  .content-body {     flex: 1;     overflow-y: auto;     padding: 24px 32px; }  .tab-section { display: none; } .tab-section.active { display: block; }  /* Metrics */ .metrics-grid {     display: grid;     grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));     gap: 16px;     margin-bottom: 24px; }  .metric-card {     background: var(--white);     padding: 20px;     border-radius: var(--radius);     box-shadow: var(--shadow);     display: flex;     align-items: center;     gap: 16px; }  .metric-icon {     width: 48px;     height: 48px;     border-radius: 12px;     display: flex;     align-items: center;     justify-content: center;     font-size: 20px; }  .metric-icon.income { background: #d1fae5; color: #059669; } .metric-icon.pending { background: #fef3c7; color: #d97706; } .metric-icon.appts { background: #e0f2fe; color: #0284c7; } .metric-icon.patients { background: #ede9fe; color: #7c3aed; }  .metric-info h3 { font-size: 12px; color: var(--gray-600); } .metric-info p { font-size: 20px; font-weight: 700; margin: 2px 0; }  .dashboard-grid {     display: grid;     grid-template-columns: 1fr 1fr;     gap: 24px; }  .card {     background: var(--white);     border-radius: var(--radius);     box-shadow: var(--shadow);     margin-bottom: 24px; }  .card-header {     padding: 16px 20px;     border-bottom: 1px solid var(--gray-200);     display: flex;     justify-content: space-between;     align-items: center; }  .card-header h3 { font-size: 15px; font-weight: 600; } .card-body { padding: 16px 20px; }  /* Tables */ .table-responsive { overflow-x: auto; } .data-table {     width: 100%;     border-collapse: collapse;     text-align: left;     font-size: 13px; }  .data-table th, .data-table td {     padding: 12px 14px;     border-bottom: 1px solid var(--gray-200); }  .data-table th { background: var(--gray-100); color: var(--gray-600); font-weight: 600; }  .badge {     padding: 4px 8px;     border-radius: 20px;     font-size: 11px;     font-weight: 600; }  .badge.success { background: #d1fae5; color: #065f46; } .badge.warning { background: #fef3c7; color: #92400e; } .badge.danger { background: #fee2e2; color: #991b1b; } .badge.info { background: #e0f2fe; color: #0369a1; } .badge.primary { background: var(--primary-light); color: var(--primary-dark); }  .btn {     padding: 8px 16px;     border-radius: 8px;     font-weight: 600;     font-size: 13px;     border: none;     cursor: pointer;     display: inline-flex;     align-items: center;     gap: 6px; }  .btn-primary { background: var(--primary); color: var(--white); } .btn-primary:hover { background: var(--primary-dark); } .btn-secondary { background: var(--gray-200); color: var(--gray-900); }  .text-btn { background: none; border: none; color: var(--primary); font-weight: 600; cursor: pointer; }  .search-box {     display: flex;     align-items: center;     background: var(--white);     border: 1px solid var(--gray-200);     border-radius: 8px;     padding: 6px 12px;     gap: 8px;     width: 240px; }  .search-box input { border: none; outline: none; font-size: 13px; width: 100%; }  .finance-summary-grid {     display: grid;     grid-template-columns: repeat(3, 1fr);     gap: 16px;     margin-bottom: 20px; }  .summary-box { padding: 16px; text-align: center; } .summary-box h4 { font-size: 12px; color: var(--gray-600); margin-bottom: 4px; } .summary-box p { font-size: 22px; font-weight: 700; }  .text-success { color: var(--success); } .text-warning { color: var(--warning); } .text-danger { color: var(--danger); }  .hospitals-grid {     display: grid;     grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));     gap: 16px; }  .hospital-card {     background: var(--white);     border-radius: var(--radius);     padding: 16px;     box-shadow: var(--shadow);     border-left: 4px solid var(--primary); }  .modal {     display: none;     position: fixed;     top: 0; left: 0; width: 100%; height: 100%;     background: rgba(0, 0, 0, 0.5);     align-items: center;     justify-content: center;     z-index: 1000; }  .modal.active { display: flex; } .modal-content {     background: var(--white);     border-radius: var(--radius);     width: 100%;     max-width: 480px;     overflow: hidden; }  .modal-header {     padding: 16px 20px;     background: var(--gray-100);     display: flex;     justify-content: space-between;     align-items: center; }  .close-btn { background: none; border: none; font-size: 20px; cursor: pointer; } .modal-body { padding: 20px; }  .form-group { margin-bottom: 14px; } .form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; } .form-group label { display: block; font-size: 12px; font-weight: 600; margin-bottom: 4px; } .form-group input, .form-group select, .form-group textarea {     width: 100%;     padding: 8px 12px;     border: 1px solid var(--gray-200);     border-radius: 8px;     font-size: 13px;     outline: none; }  .modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }  .action-icon-btn {     background: none;     border: none;     cursor: pointer;     font-size: 14px;     color: var(--gray-600);     padding: 2px 6px; } .action-icon-btn:hover { color: var(--primary); } .action-icon-btn.delete:hover { color: var(--danger); }";
                String b64Css = android.util.Base64.encodeToString(cssStr.getBytes(), android.util.Base64.NO_WRAP);
                view.evaluateJavascript("(function(){var s=document.createElement('style');s.textContent=atob('" + b64Css + "');document.head.appendChild(s);})()", null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleWebViewUrl(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && request != null && request.getUrl() != null) {
                    return handleWebViewUrl(view, request.getUrl().toString());
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar != null) progressBar.setProgress(newProgress);
            }
            
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                    ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false);
                } else {
                    pendingGeoOrigin = origin;
                    pendingGeoCallback = callback;
                    ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 2002);
                }
            }
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }
                fileUploadCallback = filePathCallback;

                // Camera intent
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.TITLE, "camera_photo");
                cameraImageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);

                // File chooser intent
                Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
                fileIntent.addCategory(Intent.CATEGORY_OPENABLE);
                fileIntent.setType("*/*");

                // Combine into chooser
                Intent chooserIntent = Intent.createChooser(fileIntent, "Select file");
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cameraIntent});

                fileUploadLauncher.launch(chooserIntent);
                return true;
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                String cookies = CookieManager.getInstance().getCookie(url);
                request.addRequestHeader("cookie", cookies);
                request.addRequestHeader("User-Agent", userAgent);
                request.setDescription("Downloading file...");
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType));
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
                Toast.makeText(getApplicationContext(), "Downloading File", Toast.LENGTH_LONG).show();
            }
        });
    }


    private boolean handleWebViewUrl(WebView view, String url) {
        if (url == null || url.trim().isEmpty()) return false;
        String lower = url.toLowerCase();
        if (lower.startsWith("tel:") || lower.startsWith("mailto:") || lower.startsWith("sms:") || lower.startsWith("smsto:") || lower.startsWith("whatsapp:") || lower.startsWith("market:") || lower.startsWith("intent:")) {
            try {
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                startActivity(intent);
                return true;
            } catch (Exception ignored) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
                    return true;
                } catch (Exception ignoredAgain) {
                    return true;
                }
            }
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return true;
        }
        try {
            java.net.URL baseUrl = new java.net.URL(WEBSITE_URL);
            java.net.URL targetUrl = new java.net.URL(url);
            if (targetUrl.getHost() != null && !targetUrl.getHost().equalsIgnoreCase(baseUrl.getHost())) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
                startActivity(browserIntent);
                return true;
            }
        } catch (Exception e) { /* ignore, load in webview */ }
        view.loadUrl(url);
        return true;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getData() != null) {
            String deepUrl = intent.getData().toString();
            if (deepUrl.startsWith("http")) {
                webView.loadUrl(deepUrl);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private final ActivityResultLauncher<Intent> fileUploadLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
            if (fileUploadCallback == null) return;
            if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                fileUploadCallback.onReceiveValue(new Uri[]{result.getData().getData()});
            } else if (result.getResultCode() == RESULT_OK && cameraImageUri != null) {
                fileUploadCallback.onReceiveValue(new Uri[]{cameraImageUri});
            } else {
                fileUploadCallback.onReceiveValue(null);
            }
            fileUploadCallback = null;
        });

    private void authenticateUser() {
        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) { return; }
        
        webView.setVisibility(View.INVISIBLE);
        
        java.util.concurrent.Executor executor = getMainExecutor();
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                webView.setVisibility(View.VISIBLE);
            }
            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    finish();
                }
            }
        });
        
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock App")
            .setSubtitle("Verify your identity to continue")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build();
        
        biometricPrompt.authenticate(promptInfo);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2002) {
            boolean granted = false;
            if (grantResults != null) {
                for (int result : grantResults) {
                    if (result == PackageManager.PERMISSION_GRANTED) {
                        granted = true;
                        break;
                    }
                }
            }
            if (pendingGeoCallback != null) {
                pendingGeoCallback.invoke(pendingGeoOrigin, granted, false);
                pendingGeoCallback = null;
                pendingGeoOrigin = null;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
        }
    }
}
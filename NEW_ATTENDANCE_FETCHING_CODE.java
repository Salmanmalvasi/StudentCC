// ========== REPLACE downloadAttendance() METHOD IN VTOPService.java ==========
// This new implementation uses direct HTTP requests instead of broken JavaScript/AJAX

/**
 * Download attendance using direct HTTP POST to VTOP.
 * This bypasses the broken jQuery/AJAX JavaScript execution.
 */
private void downloadAttendance() {
    updateProgress(R.string.downloading_attendance);

    // Check if WebView is still valid
    if (webView == null || isWebViewDestroyed) {
        android.util.Log.w("VTOPService", "WebView null/destroyed, skipping to detailed attendance");
        downloadDetailedAttendance();
        return;
    }

    // Extract CSRF token and authorizedID with minimal JavaScript
    webView.evaluateJavascript(
        "(function() {" +
        "  return JSON.stringify({" +
        "    csrf: $('input[name=\"_csrf\"]').val() || ''," +
        "    authorizedID: $('#authorizedIDX').val() || $('#authorizedID').val() || ''" +
        "  });" +
        "})();",
        tokensJson -> {
            try {
                JSONObject tokens = new JSONObject(tokensJson);
                String csrf = tokens.getString("csrf");
                String authorizedID = tokens.getString("authorizedID");
                
                if (csrf.isEmpty() || authorizedID.isEmpty()) {
                    android.util.Log.e("VTOPService", "Could not extract CSRF or authorizedID");
                    error(601, "Failed to get authentication tokens");
                    return;
                }
                
                android.util.Log.d("VTOPService", "Fetching attendance via HTTP POST");
                fetchAttendanceViaHTTP(csrf, authorizedID);
                
            } catch (Exception e) {
                android.util.Log.e("VTOPService", "Token extraction error: " + e.getMessage(), e);
                error(601, e.getLocalizedMessage());
            }
        }
    );
}

/**
 * Fetch attendance using direct HTTP POST request to VTOP endpoint
 */
private void fetchAttendanceViaHTTP(String csrf, String authorizedID) {
    new Thread(() -> {
        try {
            // Build POST data
            String postData = "_csrf=" + java.net.URLEncoder.encode(csrf, "UTF-8") +
                            "&authorizedID=" + java.net.URLEncoder.encode(authorizedID, "UTF-8") +
                            "&semesterSubId=" + java.net.URLEncoder.encode(semesterID, "UTF-8") +
                            "&x=" + System.currentTimeMillis();
            
            // Create HTTPS connection
            java.net.URL url = new java.net.URL("https://vtopcc.vit.ac.in/vtop/processViewStudentAttendance");
            javax.net.ssl.HttpsURLConnection conn = (javax.net.ssl.HttpsURLConnection) url.openConnection();
            
            // Get cookies from WebView
            android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
            String cookies = cookieManager.getCookie("https://vtopcc.vit.ac.in");
            
            // Set request headers
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Cookie", cookies != null ? cookies : "");
            conn.setRequestProperty("Referer", "https://vtopcc.vit.ac.in/vtop/open/page");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) StudentCC");
            conn.setDoOutput(true);
            
            // Send POST data
            java.io.OutputStream os = conn.getOutputStream();
            os.write(postData.getBytes("UTF-8"));
            os.flush();
            os.close();
            
            // Read response
            int responseCode = conn.getResponseCode();
            android.util.Log.d("VTOPService", "HTTP response code: " + responseCode);
            
            if (responseCode == 200) {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), "UTF-8")
                );
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // Parse HTML on main thread
                String htmlResponse = response.toString();
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                    parseAttendanceHTML(htmlResponse)
                );
            } else {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                    error(601, "HTTP error: " + responseCode)
                );
            }
            
            conn.disconnect();
            
        } catch (Exception e) {
            android.util.Log.e("VTOPService", "HTTP fetch error: " + e.getMessage(), e);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                error(601, e.getLocalizedMessage())
            );
        }
    }).start();
}

/**
 * Parse attendance HTML table from VTOP response
 */
private void parseAttendanceHTML(String htmlResponse) {
    try {
        // Parse HTML with JSoup
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(htmlResponse);
        org.jsoup.nodes.Element table = doc.getElementById("getStudentDetails");
        
        if (table == null) {
            android.util.Log.w("VTOPService", "No attendance table found");
            error(601, "No attendance data available");
            return;
        }
        
        // Find column indices from header row
        org.jsoup.select.Elements headers = table.select("th");
        int courseTypeIdx = -1, slotIdx = -1, attendedIdx = -1, totalIdx = -1, percentageIdx = -1;
        
        for (int i = 0; i < headers.size(); i++) {
            String heading = headers.get(i).text().toLowerCase();
            if (heading.contains("course") && heading.contains("type")) {
                courseTypeIdx = i;
            } else if (heading.contains("slot")) {
                slotIdx = i;
            } else if (heading.contains("attended")) {
                attendedIdx = i;
            } else if (heading.contains("total")) {
                totalIdx = i;
            } else if (heading.contains("percentage")) {
                percentageIdx = i;
            }
        }
        
        android.util.Log.d("VTOPService", String.format("Columns - Type:%d Slot:%d Attended:%d Total:%d Pct:%d", 
            courseTypeIdx, slotIdx, attendedIdx, totalIdx, percentageIdx));
        
        // Parse table rows
        org.jsoup.select.Elements rows = table.select("tbody tr");
        List<Attendance> attendance = new ArrayList<>();
        int attendedClasses = 0;
        int totalClasses = 0;
        
        for (int i = 0; i < rows.size(); i++) {
            org.jsoup.select.Elements cells = rows.get(i).select("td");
            
            if (cells.size() < 10) continue; // Skip invalid rows
            
            try {
                Attendance item = new Attendance();
                item.id = i + 1;
                
                // Extract course type
                String courseTypeText = cells.get(courseTypeIdx).text().trim();
                int courseType = courseTypeText.toLowerCase().contains("lab") ? 
                                Course.TYPE_LAB : Course.TYPE_THEORY;
                
                // Extract slot (first part if multiple slots like "A1+TA1")
                String slotText = cells.get(slotIdx).text().trim();
                String[] slots = slotText.split("\\+");
                String slot = slots.length > 0 ? slots[0].trim() : slotText;
                
                item.courseId = this.getCourseId(slot, courseType);
                
                // Extract attendance numbers
                String attendedText = cells.get(attendedIdx).text().trim();
                String totalText = cells.get(totalIdx).text().trim();
                
                item.attended = attendedText.isEmpty() ? null : Integer.parseInt(attendedText);
                item.total = totalText.isEmpty() ? null : Integer.parseInt(totalText);
                
                if (item.attended != null && item.total != null && item.total != 0) {
                    // Calculate with 2 decimal precision
                    item.percentage = (int) Math.round((item.attended * 10000.0) / item.total) / 100;
                    attendedClasses += item.attended;
                    totalClasses += item.total;
                }
                
                attendance.add(item);
                
            } catch (Exception e) {
                android.util.Log.w("VTOPService", "Row parse error at " + i + ": " + e.getMessage());
            }
        }
        
        // Calculate overall attendance with 2 decimal precision
        float overallAttendance = 0.0f;
        if (totalClasses != 0) {
            overallAttendance = Math.round((attendedClasses * 10000.0) / totalClasses) / 100.0f;
        }
        
        android.util.Log.d("VTOPService", String.format("Attendance - Attended:%d Total:%d Overall:%.2f%%", 
            attendedClasses, totalClasses, overallAttendance));
        
        // Save to preferences
        sharedPreferences.edit()
            .putFloat("overallAttendance", overallAttendance)
            .putInt("attendedClasses", attendedClasses)
            .putInt("totalClasses", totalClasses)
            .apply();
        
        // Save to database
        AttendanceDao attendanceDao = appDatabase.attendanceDao();
        Observable<Object> deleteObs = Observable.fromCompletable(attendanceDao.delete());
        Observable<Object> insertObs = Observable.fromCompletable(attendanceDao.insert(attendance));
        
        Observable.concat(deleteObs, insertObs)
            .subscribeOn(Schedulers.single())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Observer<Object>() {
                @Override
                public void onSubscribe(@NonNull Disposable d) {}
                
                @Override
                public void onNext(@NonNull Object o) {}
                
                @Override
                public void onError(@NonNull Throwable e) {
                    error(602, e.getLocalizedMessage());
                }
                
                @Override
                public void onComplete() {
                    downloadDetailedAttendance();
                }
            });
            
    } catch (Exception e) {
        android.util.Log.e("VTOPService", "HTML parse error: " + e.getMessage(), e);
        error(601, e.getLocalizedMessage());
    }
}

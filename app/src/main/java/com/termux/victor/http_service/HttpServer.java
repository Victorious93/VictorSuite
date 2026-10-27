package com.termux.victor.http_service;

import static android.os.Build.VERSION_CODES.R;

import android.os.Environment;
import android.util.Log;

import com.example.xh_lib.utils.UUtils;
import com.termux.R;
import com.termux.shared.termux.TermuxConstants;
import com.termux.victor.url.FileUrl;
import com.termux.victor.utils.FileIOUtils;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.ProgressListener;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.util.Streams;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * Created by wangmingxing on 2017/6/20.
 */

public class HttpServer extends NanoHTTPD {
    private static final String TAG = "HttpServer";
    private NanoFileUpload mFileUpload;
    private OnStatusUpdateListener mStatusUpdateListener;

    interface OnStatusUpdateListener {
        void onUploadingProgressUpdate(int progress);
        void onUploadingFile(File file, boolean done);
        void onDownloadingFile(File file, boolean done);
    }

    class DownloadResponse extends Response {
        private File downloadFile;

        DownloadResponse(File downloadFile, InputStream stream) {
            super(Response.Status.OK, "application/octet-stream", stream, downloadFile.length());
            this.downloadFile = downloadFile;
        }

        @Override
        protected void send(OutputStream outputStream) {
            super.send(outputStream);
            if (mStatusUpdateListener != null) {
                mStatusUpdateListener.onDownloadingFile(downloadFile, true);
            }
        }
    }

    public HttpServer(int port) {
        super(port);
        mFileUpload = new NanoFileUpload(new DiskFileItemFactory());
        mFileUpload.setProgressListener(new ProgressListener() {
            int progress = 0;
            @Override
            public void update(long pBytesRead, long pContentLength, int pItems) {
                //Log.d(TAG, pBytesRead + " bytes has been read, totol " + pContentLength + " bytes");
                if (mStatusUpdateListener != null) {
                    int p = (int) (pBytesRead * 100 / pContentLength);
                    if (p != progress) {
                        progress = p;
                        mStatusUpdateListener.onUploadingProgressUpdate(progress);
                    }
                }
            }
        });
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();
        Map<String, String> header = session.getHeaders();
        Map<String, String> parms = session.getParms();
        String answer = "Success!";
        Log.d(TAG, "uri=" + uri);
        Log.d(TAG, "method=" + method);
        Log.d(TAG, "header=" + header);
        Log.d(TAG, "params=" + parms);

        // for file upload
        if (NanoFileUpload.isMultipartContent(session)) {
            try {
                FileItemIterator iterator = mFileUpload.getItemIterator(session);
                while (iterator.hasNext()) {
                    FileItemStream item = iterator.next();
                    String name = item.getFieldName();
                    InputStream inputStream = item.openStream();
                    if (item.isFormField()) {
                        Log.d(TAG, "Item is form filed, name=" +
                            name + ",value=" + Streams.asString(inputStream));
                    } else {
                        String fileName = item.getName();
                        Log.d(TAG, "Item is file field, name=" + name + ",fileName=" + fileName);

                        // VictorSuite modify {@
                        // The client controls this name; keep only the file name so "../" cannot
                        // write outside the Downloads folder.
                        String safeName = HttpPathGuard.safeUploadName(fileName);
                        if (safeName == null) {
                            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Bad file name");
                        }
                        File file = new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS), safeName);
                        // @}
                        String path = file.getAbsolutePath();
                        Log.d(TAG, "Save file to " + path);
                        if (mStatusUpdateListener != null) {
                            mStatusUpdateListener.onUploadingFile(file, false);
                        }

                        FileOutputStream fos = new FileOutputStream(file);
                        Streams.copy(inputStream, fos, true);
                        if (mStatusUpdateListener != null) {
                            mStatusUpdateListener.onUploadingFile(file, true);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                // VictorSuite modify {@
                // Was: fell through to "Success!", so a failed upload looked like a good one.
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Upload failed");
                // @}
            }
        } else if (method.equals(Method.GET)) {
            // for file browse and download
            File rootFile = new File(FileUrl.INSTANCE.getMainFilesUrl());
            uri = uri.replace(rootFile.getAbsolutePath(), "");
            // VictorSuite modify {@
            // Resolve inside the root only; "/../" must not reach files outside it.
            File serveRoot = rootFile;
            File resolved = HttpPathGuard.resolveInside(rootFile, uri);
            if (resolved == null) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "Forbidden");
            }
            rootFile = resolved;
            // @}
            if (!rootFile.exists()) {
                return newFixedLengthResponse("Error! No such file or dirctory");
            }

            if (rootFile.isDirectory()) {
                // list directory files
                Log.d(TAG, "list " + rootFile.getPath());
                File[] files = rootFile.listFiles();
                File file1 = new File(FileUrl.INSTANCE.getMainFilesUrl(),  "css1.css");
                answer = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; " +
                    "charset=utf-8\"><title> VictorSuite File Browser</title><link rel=\"stylesheet\" href=\"" + file1.getAbsolutePath() + "\"  type=\"text/css\"/>" +
                    "<div id=\"title111\">VictorSuite File Browser</div>";

                if (files != null && files.length > 0) {
                    Arrays.sort(
                        files,
                        new FileComparator()
                    );
                }
              /*  answer += "<a id=\"btn2\" href=\"" + rootFile.getParentFile().getAbsolutePath()
                    + "\" alt = \"\">" + UUtils.getString(com.termux.R.string.open_ttyd_http)
                    + "</a>";
                answer += "<a id=\"btn2\" href=\"" + rootFile.getParentFile().getAbsolutePath()
                    + "\" alt = \"\">" + UUtils.getString(com.termux.R.string.open_file_http)
                    + "</a>";*/
                // VictorSuite modify {@
                // At the serve root, "..." stays put: the real parent is outside the root and 404s.
                File parentDir = rootFile.equals(serveRoot) ? serveRoot : rootFile.getParentFile();
                answer += "<a id=\"btn2\" href=\"" + HttpPathGuard.escapeHtml(HttpPathGuard.encodePath(parentDir.getAbsolutePath()))
                    + "\" alt = \"\">..."
                    + "</a>";
                // @}
                if (files == null) {
                    files = new File[0];
                }
                for (File file : files) {

                    String isFile = UUtils.getString(com.termux.R.string.http_folder);
                    String isFileLink = "";
                    if (file.isDirectory()) {
                        isFile = UUtils.getString(com.termux.R.string.http_folder);
                        isFileLink = file.getName() + "/";
                    } else {
                        isFile = UUtils.getString(com.termux.R.string.http_file);
                        isFileLink = file.getName();
                    }
                    // VictorSuite modify {@
                    answer += "<a id=\"btn2\" href=\"" + HttpPathGuard.escapeHtml(HttpPathGuard.encodePath(file.getAbsolutePath()))
                        + "\" alt = \"\">" + HttpPathGuard.escapeHtml(isFileLink)
                        + "</a>";
                    // @}
                    /**
                     *
                     * <br>[time: "
                     *                         +  UUtils.getFtpDate(file.lastModified())
                     *                         + "] [size:" + FileIOUtils.INSTANCE.formatFileSize(file.length())
                     *                         + "] [type:" + isFile
                     *                         + "]<br>
                     *
                     */
                }

                answer += "</head></html>";
            } else {
                // serve file download
                InputStream inputStream;
                Response response = null;
                Log.d(TAG, "downloading file " + rootFile.getAbsolutePath());
                if (mStatusUpdateListener != null) {
                    mStatusUpdateListener.onDownloadingFile(rootFile, false);
                }

                try {
                    inputStream = new FileInputStream(rootFile);
                    response = new DownloadResponse(rootFile, inputStream);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (response != null) {
                    response.addHeader(
                        "Content-Disposition", "attachment; filename=\"" + HttpPathGuard.headerSafeFileName(rootFile.getName()) + "\"");
                    return response;
                } else {
                    return newFixedLengthResponse("Error downloading file!");
                }
            }
        }

        return newFixedLengthResponse(answer);
    }


    public void setOnStatusUpdateListener(OnStatusUpdateListener listener) {
        mStatusUpdateListener = listener;
    }
}

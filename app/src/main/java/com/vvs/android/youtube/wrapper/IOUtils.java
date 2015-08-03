package com.vvs.android.youtube.wrapper;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by vasiliy.simonov on 24/07/15.
 */
public class IOUtils {

    public static final String inputStreamToString( final InputStream is ) throws IOException {
        final StringBuilder out = new StringBuilder();
        final BufferedReader in = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        try {
            String separator = System.getProperty("line.separator");
            String line;
            while ((line = in.readLine()) != null)
                out.append(line).append(separator);
        } finally {
            in.close();
        }

        return out.toString();
    }

    public static void closeSilently(final Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception exc) {
                // Do nothing
            }
        }
    }
}

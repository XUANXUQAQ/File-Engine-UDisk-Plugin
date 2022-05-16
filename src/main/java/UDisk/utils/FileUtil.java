package UDisk.utils;

import java.io.*;

public class FileUtil {

    public static String getParentPath(String path) {
        File f = new File(path);
        return f.getParentFile().getAbsolutePath();
    }

    public static String getFileName(String path) {
        if (path != null) {
            int index = path.lastIndexOf(File.separator);
            return path.substring(index + 1);
        }
        return "";
    }

//    public static void copyFile(InputStream source, File dest) {
//        try (BufferedInputStream bis = new BufferedInputStream(source);
//             BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dest))) {
//            byte[] buffer = new byte[8192];
//            int count = bis.read(buffer);
//            while (count != -1) {
//                //使用缓冲流写数据
//                bos.write(buffer, 0, count);
//                //刷新
//                bos.flush();
//                count = bis.read(buffer);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

}

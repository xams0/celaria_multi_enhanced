package server.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

public class FileUtil {

	public static String[] getMapFileList(String directory){
		List<String> fileList = new LinkedList<String>();

		File folder = new File(directory);
		File[] listOfFiles = folder.listFiles();
		if (listOfFiles == null) {
			return new String[0];
		}

		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile()) {
				if(listOfFiles[i].getName().toLowerCase().endsWith(".cmap")){
					fileList.add(listOfFiles[i].getName());
				}
			}
		}
		fileList.sort(String.CASE_INSENSITIVE_ORDER);// rotation in alphabetical order
		return fileList.toArray(new String[fileList.size()]);
	}
	
	public static byte[] loadBinaryFile(String file) throws IOException{
		File mapFile = new File(file);
        //Instantiate the input stread
        InputStream insputStream = new FileInputStream(mapFile);
        long length = mapFile.length();
        byte[] binaryData = new byte[(int) length];
        
        
        insputStream.read(binaryData);
        insputStream.close();
        
        return binaryData;

	}

}

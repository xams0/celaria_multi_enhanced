package server.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class FileUtil {

	/**
	 * Order of the maps (launcher), in the map folder: one file name per line, "random = true" for a random rotation.
	 * Maps of the folder that aren't listed come after, in alphabetical order.
	 */
	public static final String MAP_ORDER_FILE = "maplist.txt";

	private static List<String> readMapOrder(String directory) {
		List<String> lines = new ArrayList<String>();
		try {
			for (String line : Files.readAllLines(new File(directory, MAP_ORDER_FILE).toPath(), StandardCharsets.UTF_8)) {
				line = line.trim();
				if (line.isEmpty() == false && line.startsWith("#") == false) {
					lines.add(line);
				}
			}
		} catch (IOException e) {
			// no order file: alphabetical order
		}
		return lines;
	}

	/** the maps are played in a random order (shuffled, each map once before a map comes back) */
	public static boolean isRandomMapOrder(String directory) {
		for (String line : readMapOrder(directory)) {
			String l = line.replace(" ", "").toLowerCase();
			if (l.startsWith("random=")) {
				return l.equals("random=true") || l.equals("random=1");
			}
		}
		return false;
	}

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
		// then the order of the launcher (maplist.txt)
		Map<String, Integer> rank = new HashMap<String, Integer>();
		for (String line : readMapOrder(directory)) {
			if (line.toLowerCase().endsWith(".cmap") && rank.containsKey(line.toLowerCase()) == false) {
				rank.put(line.toLowerCase(), rank.size());
			}
		}
		if (rank.isEmpty() == false) {
			fileList.sort((a, b) -> Integer.compare(rank.getOrDefault(a.toLowerCase(), Integer.MAX_VALUE), rank.getOrDefault(b.toLowerCase(), Integer.MAX_VALUE)));
		}
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

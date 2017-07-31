package com.javatechig.listallfiles;

import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Created by chiaying.wu on 2017/7/17.
 */

public class FileSearcher {
    //getting SDcard root path
    private File m_root = new File(Environment.getExternalStorageDirectory().getAbsolutePath());
//    private File m_root = new File("/storage/emulated/0/Download");

    private ArrayList<File> m_arrltDirectories = new ArrayList<File>();
    private ArrayList<File> m_arrltFoundFiles = new ArrayList<File>();
    private ArrayList<File> m_arrltDupFiles = new ArrayList<File>();
    private ArrayList<File> m_arrltTempFiles = new ArrayList<File>(); //new container for matched files
    private File m_lastFoundFile;

    private String m_strFileName;
    private Date m_startDate, m_endDate;
    private long m_minSize, m_maxSize;
    private List<String> m_inputTextList;

    private static final int FILE_NAME = 0;
    private static final int START_DATE = 1;
    private static final int END_DATE = 2;
    private static final int MIN_SIZE = 3;
    private static final int MAX_SIZE = 4;

    private Boolean m_isFinishFiltering = false;

    public FileSearcher() {
    }

    public void setDirectoryPath(File dir) {
        this.m_root = dir;
    }

    private void setInputVariables(List<String> inputTextList) {
        this.m_inputTextList = inputTextList;

        //parse text values
        int i = 0, iInputTextListSize = inputTextList.size();
        while (i < iInputTextListSize) {
            if (inputTextList.get(i) != null) { //has text value
                switch (i) {
                    case FILE_NAME:
                        this.m_strFileName = inputTextList.get(i);
                        break;
                    case START_DATE:
                        //get text
                        String strStartDate = inputTextList.get(i);
                        //parse text
                        int iArrStartDate[] = parseDateText(strStartDate);
                        //format date
                        Date startDate = convertToDate(iArrStartDate[0], iArrStartDate[1], iArrStartDate[2], false); //param: year, month, day
                        this.m_startDate = startDate;
                        break;
                    case END_DATE:
                        //get text
                        String strEndDate = inputTextList.get(i);
                        //parse text
                        int iArrEndDate[] = parseDateText(strEndDate);
                        //format date
                        Date endDate = convertToDate(iArrEndDate[0], iArrEndDate[1], iArrEndDate[2], true); //param: year, month, day
                        this.m_endDate = endDate;
                        break;
                    case MIN_SIZE:
                        long min_size = Long.parseLong(inputTextList.get(i)) * 1024 * 1024; //Convert megabytes to bytes
                        this.m_minSize = min_size;
                        break;
                    case MAX_SIZE:
                        long max_size = Long.parseLong(inputTextList.get(i)) * 1024 * 1024; //Convert megabytes to bytes
                        this.m_maxSize = max_size;
                        break;
                }

            }
            i++;
        }

    }

    public Date getInputStartDate() {
        return m_startDate;
    }

    public Date getInputEndDate() {
        return m_endDate;
    }

    public long getInputMinSize() {
        return m_minSize;
    }

    public long getInputMaxSize() {
        return m_maxSize;
    }

    public String getFileName() {
        return m_strFileName;
    }

    private int[] parseDateText(String strDate) {
        int[] iArrDate = new int[3];
        String[] strArrDate = strDate.split("/");
        for (int i = 0; i < 3; i++) {
            iArrDate[i] = Integer.parseInt(strArrDate[i]);
        }
        return iArrDate;
    }

    public Date convertToDate(int year, int month, int day, Boolean isEndDate) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        if (isEndDate) day++;
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);// for 0 min
        calendar.set(Calendar.SECOND, 0);// for 0 sec
        Date date = new Date(calendar.getTimeInMillis());

        return date;
    }

    public void searchFiles(final CallBack callBack, final List<String> inputTextList){
        if(inputTextList != null)  //has input
            setInputVariables(inputTextList);

        SearchThread searchThread = new SearchThread();
        searchThread.setPriority(1);
        searchThread.start();

        FilterThread filterThread = new FilterThread(inputTextList, callBack);
        filterThread.start();


    }
    class SearchThread extends Thread{
        @Override
        public void run() {
            super.run();
            searchUnderRootPath();
        }
    }

    class FilterThread extends Thread{
        private List<String> inputTextList;
        private CallBack callBack;

        FilterThread(List<String> inputTextList, CallBack callBack) {
            this.inputTextList = inputTextList;
            this.callBack = callBack;
        }

        @Override
        public void run() {
            super.run();
            try {
                sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if(inputTextList != null) { //has input
                filterSearchByInput();
                callBack.receiveFiles(m_arrltTempFiles, m_isFinishFiltering);
            }else {
                callBack.receiveFiles(m_arrltTempFiles, m_isFinishFiltering);
            }
        }
    }

    private void searchUnderRootPath() {
        m_arrltDirectories.add(m_root); //based on root path

        //scan directory paths
        int i = 0;
        while (i < m_arrltDirectories.size()) {
            getFile(m_arrltDirectories.get(i));
            i++;
        }
    }

    private void getFile(File dir) {
        File listFile[] = dir.listFiles();

        if (listFile != null && listFile.length > 0) {
            for (int i = 0; i < listFile.length; i++) {
                if (listFile[i].isDirectory()) { //directory
                    m_arrltDirectories.add(listFile[i]); //store directory path into list
                } else { //file
                    m_arrltFoundFiles.add(listFile[i]);
                    m_lastFoundFile = listFile[i];
                }
            }
        }
    }

    public void filterSearchByInput() {//Filter files found by input fields
        int iInputTextListSize = m_inputTextList.size();
        File currentFile = null;
        while (m_arrltFoundFiles.size() > 0 || currentFile != m_lastFoundFile){
            currentFile = m_arrltFoundFiles.get(0);
            File matchedFile = null;
            int inputField = 0;
    scanner:while (inputField < iInputTextListSize) { //filter by each input field
                if (m_inputTextList.get(inputField) != null) {//has input text
                    switch (inputField) {
                        case FILE_NAME:
                            if (!currentFile.getName().contains(getFileName())) {
                                m_arrltFoundFiles.remove(currentFile);
                                break scanner;
                            }
                            else{
                                matchedFile = currentFile;
                            }
                            break;
                        case START_DATE:
                            if (new Date(currentFile.lastModified()).before(getInputStartDate())) {
                                m_arrltFoundFiles.remove(currentFile);
                                matchedFile = null;
                                break scanner;
                            }
                            else{
                                matchedFile = currentFile;
                            }
                            break;
                        case END_DATE:
                            if (new Date(currentFile.lastModified()).after(getInputEndDate())) {
                                m_arrltFoundFiles.remove(currentFile);
                                matchedFile = null;
                                break scanner;
                            }
                            else{
                                matchedFile = currentFile;
                            }
                            break;
                        case MIN_SIZE:
                            if (currentFile.length() < getInputMinSize()) {
                                m_arrltFoundFiles.remove(currentFile);
                                matchedFile = null;
                                break scanner;
                            }
                            else{
                                matchedFile = currentFile;
                            }
                            break;
                        case MAX_SIZE:
                            if (currentFile.length() > getInputMaxSize()) {
                                m_arrltFoundFiles.remove(currentFile);
                                matchedFile = null;
                                break scanner;
                            } else{
                                matchedFile = currentFile;
                            }
                            break;
                    }
                }
                inputField++;
            }
            if(matchedFile != null){
                m_arrltTempFiles.add(matchedFile); //Add matched file to a new arrayList
            }
            m_arrltFoundFiles.remove(currentFile);//remove file in original arraylist after authenticated
        }
        m_isFinishFiltering = true;
    }

    public ArrayList<File> searchDupFiles() {
        searchUnderRootPath();

        try {
            findDuplicatedFiles(m_arrltFoundFiles);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return m_arrltDupFiles;
    }

    private void findDuplicatedFiles(ArrayList<File> filepaths) {
        HashMap<String, String> hashmap = new HashMap<String, String>();
        for (File filepath : filepaths) {
            String strFilePath = String.valueOf(filepath);
            String md5 = null;
            try {
                md5 = MD5CheckSum.getMD5Checksum(strFilePath);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (hashmap.containsKey(md5)) {
                String original = hashmap.get(md5);
                String duplicate = strFilePath;

                // found a match between original and duplicate
                File fileOri = new File(original);
                m_arrltDupFiles.add(fileOri);

                File fileDup = new File(duplicate);
                m_arrltDupFiles.add(fileDup);
            } else {
                hashmap.put(md5, strFilePath);
            }
        }
    }
}
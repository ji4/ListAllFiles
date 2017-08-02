package com.javatechig.listallfiles;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

/**
 * Created by chiaying.wu on 2017/7/17.
 */

public class FileSearcher {
    //getting SDcard root path
//    private File m_root = new File(Environment.getExternalStorageDirectory().getAbsolutePath());
    private File m_root = new File("/storage/emulated/0/Download");

    private ArrayList<File> m_arrltDirectories = new ArrayList<File>();
    private ArrayList<File> m_arrltFoundFiles = new ArrayList<File>();
    private ArrayList<File> m_arrltDupFiles = new ArrayList<File>();
    private ArrayList<File> m_arrltResultFiles = new ArrayList<File>(); //new container for matched files

    private String m_strFileName;
    private Date m_startDate, m_endDate;
    private long m_minSize, m_maxSize;
    private List<String> m_strListInputText;

    private static final int FILE_NAME = 0;
    private static final int START_DATE = 1;
    private static final int END_DATE = 2;
    private static final int MIN_SIZE = 3;
    private static final int MAX_SIZE = 4;
    private ArrayList<InputField> m_inputFields = new ArrayList<>();

    private Boolean m_isFinishSearching = false;
    private int m_iFileFoundCount = 0;
    private int m_iFileFilteredCount = 0;

    public FileSearcher() {
    }

    public void setDirectoryPath(File dir) {
        this.m_root = dir;
    }

    private void setInputVariables(List<String> strListinputText) {
        this.m_strListInputText = strListinputText;

        //Initailize inputFields:  [[iCode, strValue], ...]
        int iInputTextListSize = strListinputText.size();
        for(int i = 0; i<iInputTextListSize; i++){
            m_inputFields.add(new InputField());
            m_inputFields.get(i).iCode = i;
            m_inputFields.get(i).strValue = m_strListInputText.get(i);
        }
        Log.d("jia,m_inputFields", String.valueOf(m_inputFields));

        //parse text values
        int iInputIndex = 0;
        for(ListIterator<InputField> iterator = m_inputFields.listIterator(); iterator.hasNext();){
            String strInputValue = iterator.next().strValue;
            if (strInputValue != null) { //has text value
                switch (iInputIndex) {
                    case FILE_NAME:
                        this.m_strFileName = strInputValue;
                        break;
                    case START_DATE:
                        //get text
                        String strStartDate = strInputValue;
                        //parse text
                        int iArrStartDate[] = parseDateText(strStartDate);
                        //format date
                        Date startDate = convertToDate(iArrStartDate[0], iArrStartDate[1], iArrStartDate[2], false); //param: year, month, day
                        this.m_startDate = startDate;
                        break;
                    case END_DATE:
                        //get text
                        String strEndDate = strInputValue;
                        //parse text
                        int iArrEndDate[] = parseDateText(strEndDate);
                        //format date
                        Date endDate = convertToDate(iArrEndDate[0], iArrEndDate[1], iArrEndDate[2], true); //param: year, month, day
                        this.m_endDate = endDate;
                        break;
                    case MIN_SIZE:
                        long min_size = Long.parseLong(strInputValue) * 1024 * 1024; //Convert megabytes to bytes
                        this.m_minSize = min_size;
                        break;
                    case MAX_SIZE:
                        long max_size = Long.parseLong(strInputValue) * 1024 * 1024; //Convert megabytes to bytes
                        this.m_maxSize = max_size;
                        break;
                }
            } else{
                iterator.remove();
            }
            iInputIndex++;
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

    public Date convertToDate(int iYear, int iMonth, int iDay, Boolean isEndDate) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, iYear);
        calendar.set(Calendar.MONTH, iMonth - 1);
        if (isEndDate) iDay++;
        calendar.set(Calendar.DAY_OF_MONTH, iDay);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);// for 0 min
        calendar.set(Calendar.SECOND, 0);// for 0 sec
        Date date = new Date(calendar.getTimeInMillis());

        return date;
    }

    /*Flow:
       'searchThread' found some files -> 'filterThread' filter files just found
    -> Make 'searchThread' continously runs -> 'filterThread' runs again -> ..loop..
    -> finishes & tell UI to stop refreshing */
    public void searchFiles(final CallBack callBack, final List<String> strListInputText){
        if(strListInputText != null)  //has input
            setInputVariables(strListInputText);

        SearchThread searchThread = new SearchThread();
        searchThread.setPriority(1); //not sure necessary
        searchThread.start();

        FilterThread filterThread = new FilterThread(callBack, strListInputText);
        filterThread.start();
    }
    class SearchThread extends Thread{
        @Override
        public void run() {
            super.run();
            Log.d("jia", "searchThread starts to run");
            searchUnderRootPath();
        }
    }

    class FilterThread extends Thread{
        private CallBack callBack;
        private List<String> inputTextList;

        FilterThread(CallBack callBack, List<String> inputTextList) {
            this.callBack = callBack;
            this.inputTextList = inputTextList;
        }

        @Override
        public void run() {
            super.run();
            Log.d("jia", "filterThread starts to run");
            try {
                sleep(200); //Make searchThread
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            /* conditions in while:
            Keep filterThread running when searching; Continue filtering files if there are files found not filtered yet after searchThread finishes*/
            while(!m_isFinishSearching || m_arrltFoundFiles.size() > 0) {
                if (inputTextList != null) { //has input
                    filterSearchByInput();
                    callBack.receiveFiles(m_arrltResultFiles);

                    try {
                        sleep(200); //Make searchThread's turn after filterThread finishes files just found
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                } else {
                    callBack.receiveFiles(m_arrltResultFiles);
                }
            }
            callBack.receiveSearchStatus(m_isFinishSearching); //tell UI to stop refreshing
            Log.d("jia", "filterThread finishes.");
        }
    }

    private void searchUnderRootPath() {
        m_arrltDirectories.add(m_root); //based on root path

        int i = 0;
        while (!m_isFinishSearching) { //Keep searchThread running
            while (i < m_arrltDirectories.size()) {//Scan directory paths
                getFile(m_arrltDirectories.get(i));
                i++;
            }
            m_isFinishSearching = true;
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
                    Log.d("jia", "the "+m_iFileFoundCount+" th file found");
                    m_iFileFoundCount++;
                }
            }
        }
    }

    private void filterSearchByInput() {//Filter files found by input fields
        int iInputFieldsSize = m_inputFields.size();
        File scanningFile;
        while (m_arrltFoundFiles.size() > 0){
            scanningFile = m_arrltFoundFiles.get(0);
            Log.d("jia", "filtering file "+m_iFileFilteredCount+": "+scanningFile);
            m_iFileFilteredCount++;
            File matchedFile = null;
            int i = 0;
            scanner:while (i < iInputFieldsSize) { //filter by each input field
                if (scanningFile != null) {//has input text
                    switch (m_inputFields.get(i).iCode) {
                        case FILE_NAME:
                            if (!scanningFile.getName().contains(
                                    getFileName())) {
                                m_arrltFoundFiles.remove(scanningFile);
                                break scanner;
                            }
                            else{
                                matchedFile = scanningFile;
                            }
                            break;
                        case START_DATE:
                            if (new Date(scanningFile.lastModified()).before(getInputStartDate())) {
                                m_arrltFoundFiles.remove(scanningFile);
                                matchedFile = null;
                                break scanner;
                            }
                            else{
                                matchedFile = scanningFile;
                            }
                            break;
                        case END_DATE:
                            if (new Date(scanningFile.lastModified()).after(getInputEndDate())) {
                                m_arrltFoundFiles.remove(scanningFile);
                                matchedFile = null;
                                break scanner;
                            }
                            else{
                                matchedFile = scanningFile;
                            }
                            break;
                        case MIN_SIZE:
                            if (scanningFile.length() < getInputMinSize()) {
                                m_arrltFoundFiles.remove(scanningFile);
                                matchedFile = null;
                                break scanner;
                            }
                            else{
                                matchedFile = scanningFile;
                            }
                            break;
                        case MAX_SIZE:
                            if (scanningFile.length() > getInputMaxSize()) {
                                m_arrltFoundFiles.remove(scanningFile);
                                matchedFile = null;
                                break scanner;
                            } else{
                                matchedFile = scanningFile;
                            }
                            break;
                    }
                }
                i++;
            }
            if(matchedFile != null){
                m_arrltResultFiles.add(matchedFile); //Add matched file to a new arrayList
            }
            m_arrltFoundFiles.remove(scanningFile);//remove file in original arraylist after authenticated
        }
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
class InputField{
    int iCode;
    String strValue;
}
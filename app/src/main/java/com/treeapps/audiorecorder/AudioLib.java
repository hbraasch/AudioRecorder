package com.treeapps.audiorecorder;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

/**
 * Created by HeinrichWork on 11/02/2015.
 */
public class AudioLib {

    private final String TAG = "AudioLib";
    private final String strAudioScratchFilename = "audioscratch";
    private final String strAudioSubfolderName = "audio";


    private String strWorkFolderPath;

    public AudioLib(String strFolderPath) {
        strWorkFolderPath = strFolderPath + "/" + strAudioSubfolderName;
        File fileDir = new File(strWorkFolderPath);
        if (!fileDir.exists()) {
            fileDir.mkdirs();
        }
    }

    public class AudioSample {
        public File filePathPcm;
        public long lngSizePcmInShorts; // Frame is a short


        /**
         * Creates a new audio object
         * @param strFilenameWithoutExt
         * @throws IOException
         */
        public AudioSample(String strFilenameWithoutExt) throws IOException {

            this.filePathPcm = new File(strWorkFolderPath + "/" + strFilenameWithoutExt + ".pcm");

            if (this.filePathPcm.exists()) {
                try {
                    DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(filePathPcm)));
                    if (!dis.markSupported()) {
                        throw new RuntimeException("Mark/Reset not supported!");
                    }
                    lngSizePcmInShorts = getSizeInShorts(dis);
                } catch (FileNotFoundException e) {
                    Log.e(TAG, "File not found when creating AudioSample", e);
                    lngSizePcmInShorts = 0;
                } catch (IOException e) {
                    Log.e(TAG, "IO exception when creating AudioSample", e);
                    lngSizePcmInShorts = 0;
                }
            } else {
                lngSizePcmInShorts = 0;
            }

        }

        public void clear() {
            if (this.filePathPcm.exists()) {
                this.filePathPcm.delete();
            }
            lngSizePcmInShorts = 0;
        }

        public String getFullFilename() {
            return filePathPcm.getAbsolutePath();
        }

        public int[] getGraphBuffer(double fltRmsStartFrame, int intRmsFramesAmount, double fltRmsFrameSizeInSingles) throws IOException {
            int intRmsFrameSizeInSingles = (int) fltRmsFrameSizeInSingles;

            if (!exists()) {
                throw new IOException("CurrentFile does not exist");
            }
            long lngDataAmountInBytes = roundDownToEven((long) (intRmsFramesAmount * (fltRmsFrameSizeInSingles * 2)));
            long lngStartByte = roundDownToEven((long) (fltRmsStartFrame * fltRmsFrameSizeInSingles * 2));
            long lngEndByte = roundDownToEven((long) (lngStartByte + lngDataAmountInBytes));

            if (lngStartByte > lngSizePcmInShorts * 2) {
                lngStartByte = 0;
            }

            if (lngEndByte > lngSizePcmInShorts * 2) {
                lngEndByte = lngSizePcmInShorts * 2;
            }

            long lngBytesToRead = lngEndByte - lngStartByte;

            int len = 0;
            long lngBytesRead = 0;
            int intRmsBufferIndex = 0;
            int bufferSize = intRmsFrameSizeInSingles * 2;
            byte[] b = new byte[bufferSize];
            DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(filePathPcm)));
            int[] intRmsBuffer = new int[intRmsFramesAmount];
            dis.skipBytes((int) lngStartByte);

            while ((len = dis.read(b, 0, bufferSize)) > -1) {
                lngBytesRead += len;

                // Convert to short
                short[] s = byte2short(b);

                // Calc and save RMS
                double sum = 0;
                for (int j = 0; j < s.length; j++) {
                    sum += s[j] * s[j];
                }
                final double amplitude = sum / s.length;
                intRmsBuffer[intRmsBufferIndex] = ((int) Math.sqrt(amplitude));
                intRmsBufferIndex += 1;

                // Break if needed
                if (lngBytesRead >= lngBytesToRead) {
                    break;
                }
                if (intRmsBufferIndex >= intRmsBuffer.length) {
                    break;
                }
            }
            dis.close();
            return intRmsBuffer;
        }


        public long getDataAmountInRmsFrames(double fltOptimalDataSampleBufferSizeInSingles) {
            return (long)(Math.ceil((float) lngSizePcmInShorts /fltOptimalDataSampleBufferSizeInSingles));
        }

        // Convert bytes to shorts
        private short[] byte2short(byte[] sData) {
            byte[] bytes = sData;
            short[] shorts = new short[bytes.length/2];
            // to turn bytes to shorts as either big endian or little endian.
            ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(shorts);
            return shorts;
        }

        /**
         * Creates a new object, preloaded from source at strSourcePath
         * @param strFilenameWithoutExt
         * @param strSourceFilenameWithoutExt
         * @throws IOException
         */
        public AudioSample(String strFilenameWithoutExt, String strSourceFilenameWithoutExt) throws IOException {

            filePathPcm = new File(strWorkFolderPath + "/" + strFilenameWithoutExt + ".pcm");
            File fileSourcePath = new File(strWorkFolderPath + "/" + strSourceFilenameWithoutExt + ".pcm");
            if (!fileSourcePath.exists()) {
                throw new IOException("Source .pcm does not exist");
            }
            copyFile(fileSourcePath, filePathPcm, false);
            DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(filePathPcm)));
            lngSizePcmInShorts = getSizeInShorts(dis);
        }

        public void copyFrom(AudioSample objAudioSample) throws IOException {
            // TODO Auto-generated method stub
            copyFile(objAudioSample.filePathPcm, filePathPcm, false);
            DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(filePathPcm)));
            lngSizePcmInShorts = getSizeInShorts(dis);
        }

        public boolean exists() {
            if (lngSizePcmInShorts != 0) {
                return true;
            }
            return false;
        }

        public void trimRight(long intTrimBytesAmount) throws IOException {
            // Trim PCM file
            File fileScratch = new File(strWorkFolderPath + "\\" + strAudioScratchFilename + ".pcm");
            long lngSizePcmInFramesBefore = lngSizePcmInShorts;
            copyFile(filePathPcm, fileScratch, false);
            trimParts(fileScratch, filePathPcm, intTrimBytesAmount, true);
            DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(filePathPcm)));
            lngSizePcmInShorts = getSizeInShorts(dis);
            Log.d(TAG, "File " + filePathPcm.getName() + " right trimmed from " + lngSizePcmInFramesBefore + " to " + lngSizePcmInShorts);

        }

        public void trimLeft(long intTrimBytesAmount) throws IOException {
            // Trim PCM file
            File fileScratch = new File(strWorkFolderPath + "\\" + strAudioScratchFilename + ".pcm");
            long lngSizePcmInFramesBefore = lngSizePcmInShorts;
            copyFile(this.filePathPcm, fileScratch, false);
            trimParts(fileScratch, this.filePathPcm, intTrimBytesAmount, false);
            DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(filePathPcm)));
            lngSizePcmInShorts = getSizeInShorts(dis);
            Log.d(TAG, "File " + filePathPcm.getName() + " left trimmed from " + lngSizePcmInFramesBefore + " to " + lngSizePcmInShorts);

        }

        public void mergeInto(ArrayList<AudioSample> objFollowingSamples) throws IOException {
            // Merge PCM files
            ArrayList<String> objMergingFiles = new ArrayList<String>();
            for (AudioSample objAudioSample : objFollowingSamples) {
                if (objAudioSample.exists()) {
                    objMergingFiles.add(objAudioSample.filePathPcm.getPath());
                    Log.d(TAG, "File " + objAudioSample.filePathPcm.getName() + " of length " + objAudioSample.lngSizePcmInShorts
                            + " merged");
                }
            }
            mergeParts(objMergingFiles, filePathPcm.getPath());
            DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(filePathPcm)));
            lngSizePcmInShorts = getSizeInShorts(dis);
            Log.d(TAG, "Total PCM merged file size is " + lngSizePcmInShorts);

        }


        public void updateFileSize() throws IOException {
            DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(filePathPcm)));
            if (!dis.markSupported()) {
                throw new RuntimeException("Mark/Reset not supported!");
            }
            lngSizePcmInShorts = getSizeInShorts(dis);
        }

        public void copyTo(String strAudioEditFullFilename) {
            File fileDest = new File(strAudioEditFullFilename);
            copyFile(filePathPcm, fileDest, false);
        }

        public AudioSample copyFrom(String strAudioEditFullFilename) throws IOException {
            File fileSrc = new File(strAudioEditFullFilename);
            if (fileSrc.exists()) {
                copyFile(fileSrc, filePathPcm, false);
                updateFileSize();
            } else {
                lngSizePcmInShorts = 0;
            }
            return this;
        }



        public void createTestSignal() {
            try {
                final int MAX_VAL = 0xffff;
                DataOutputStream dout = new DataOutputStream(new FileOutputStream(filePathPcm));
                byte[] b = new byte[MAX_VAL];
                for (int i = 0; i < MAX_VAL; i++) {
                    b[i] = (byte)i;
                }
                dout.write(b,0,MAX_VAL);
                dout.close();

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }



    private long roundDownToEven(long intSkipLen) {
        // TODO Auto-generated method stub
        if ((intSkipLen % 2) == 1) {
            return intSkipLen - 1;
        }
        return intSkipLen;
    }

    public void trimParts(File fileSourcePath, File fileDestinationPath, long intTrimBytesAmount,
                          boolean boolIsTrimRight) throws IOException {
        fileDestinationPath.delete();
        DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(fileSourcePath)));
        byte[] audiodata = new byte[1024];
        final long lngSize = getSizeInBytes(dis);
        if (intTrimBytesAmount > lngSize) {
            intTrimBytesAmount = lngSize;
        }
        if (!boolIsTrimRight) {
            // Trim left side away
            dis = new DataInputStream(new BufferedInputStream(new FileInputStream(fileSourcePath))); // Reset
            FileOutputStream os = new FileOutputStream(fileDestinationPath);
            final long intSkipLen = roundDownToEven(intTrimBytesAmount);
            dis.skip(intSkipLen);
            while (dis.available() > 0) {
                int ret = dis.read(audiodata, 0, audiodata.length);
                if (ret != -1) {
                    os.write(audiodata, 0, ret);
                } else
                    break;
            }
            dis.close();
            os.close();
        } else {
            // Trim right side away
            dis = new DataInputStream(new BufferedInputStream(new FileInputStream(fileSourcePath))); // Reset
            FileOutputStream os = new FileOutputStream(fileDestinationPath);
            long intReadAmount = roundDownToEven(intTrimBytesAmount);
            long intReadCount = 0;
            while (dis.available() > 0) {
                int ret = dis.read(audiodata, 0, audiodata.length);
                if (ret != -1) {
                    intReadCount += ret;
                    if (intReadCount <= intReadAmount) {
                        os.write(audiodata, 0, ret);
                    } else {
                        int intOverRead = (int) (intReadCount - intReadAmount);
                        os.write(audiodata, 0, ret - intOverRead);
                        break;
                    }
                } else
                    break;
            }
            dis.close();
            os.close();
        }
    }



    private void mergeParts(ArrayList<String> nameList, String strDestinationPath) throws IOException {
        FileChannel inChannel = null;
        FileChannel outChannel = new FileOutputStream(strDestinationPath, false).getChannel();
        for (String strFileFullName : nameList) {
            inChannel = new FileInputStream(strFileFullName).getChannel();
            outChannel.transferFrom(inChannel, outChannel.size(), inChannel.size());
        }
        if (inChannel != null)  inChannel.close();
        outChannel.close();
    }


    private void write(byte[] allFilesContent, String strDestinationPath) {
        File file = new File(strDestinationPath);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            // Writes bytes from the specified byte array to this file output
            // stream
            fos.write(allFilesContent);
        } catch (FileNotFoundException e) {
            System.out.println("File not found" + e);
        } catch (IOException ioe) {
            System.out.println("Exception while writing file " + ioe);
        } finally {
            // close the streams using close method
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException ioe) {
                System.out.println("Error while closing stream: " + ioe);
            }
        }
    }

    public static void copyFile(File src, File dst, boolean aAppend) {
        InputStream inStream = null;
        OutputStream outStream = null;

        try {
            try {
                byte[] bucket = new byte[32 * 1024];

                inStream = new BufferedInputStream(new FileInputStream(src));
                outStream = new BufferedOutputStream(new FileOutputStream(dst, aAppend));

                int bytesRead = 0;
                while (bytesRead != -1) {
                    bytesRead = inStream.read(bucket); // -1, 0, or more

                    if (bytesRead > 0) {
                        outStream.write(bucket, 0, bytesRead);
                    }
                }
            } finally {
                if (inStream != null)
                    inStream.close();
                if (outStream != null)
                    outStream.close();
            }
        } catch (FileNotFoundException ex) {
            System.out.println("Error while closing stream: " + ex);
        } catch (IOException ex) {
            System.out.println("Error while closing stream: " + ex);
        }
    }

    private long getSizeInBytes(InputStream is) throws IOException {

        int len;
        long size = 0;
        byte[] buf;
        int bufSize = 1024;

        buf = new byte[bufSize];
        while ((len = is.read(buf, 0, bufSize)) != -1)
            size += len;

        return size;
    }

    private long getSizeInShorts(InputStream is) throws IOException {
        return getSizeInBytes(is)/2;
    }
}

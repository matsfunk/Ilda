package Ilda;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;

/**
 * This class reads a file and passes the data to frames and points.
 * <p/>
 * Ilda files are explained here: http://www.laserist.org/StandardsDocs/IDTF05-finaldraft.pdf
 * This document only mentions Ilda V0, 1, 2 and 3, no V4 and V5 so here's a breakdown:
 * ILDA V0 is 3D and uses palettes
 * ILDA V1 is 2D and uses palettes
 * ILDA V2 is a palette
 * ILDA V3 is a 24-bit palette, but was discontinued and is not a part of the official standard anymore
 * ILDA V4 is 3D with true-colour information in BGR format
 * ILDA V5 is 2D with true-colour information in BGR format
 * <p/>
 * An Ilda file is composed of headers that always start with "ILDA", followed by three zeros and the version number.
 * A complete header is 32 bytes.
 * After the header, the data follows. In case of a palette (V2), each data point has three bytes: R, G and B.
 * In case of a frame (V0/1/4/5), the X, Y and Z (for 3D frames) values are spread out over two bytes
 * Then either two status bytes follow with a blanking bit and palette colour number, or BGR values.
 */
public class IldaReader {
    protected String location;
    protected byte[] b;
    protected ArrayList<Integer> framePositions = new ArrayList();
    IldaPalette palette;
    Ilda ilda;

    public IldaReader(Ilda ilda, String location) {
        this.location = location;
        this.ilda = ilda;
        try {
            b = Files.readAllBytes(new File(location).toPath());
            ilda.status.add("Found bytes, length: " + b.length);
        } catch (Exception e) {
            ilda.status.clear();
            ilda.status.add("Could not read file");

        }
        ilda.status.add("Succesfully read file " + location);
    }

    public IldaReader(Ilda ilda, File file) {
        this(ilda, file.getAbsolutePath());
    }

    public ArrayList<IldaFrame> getFramesFromBytes() {
        return getFramesFromBytes(b);
    }

    public ArrayList<IldaFrame> getFramesFromBytes(byte[] b) {

        ArrayList<IldaFrame> theFrames = new ArrayList<IldaFrame>();
        if (b == null) {
            ilda.status.add("Found no bytes to parse as frames");
            return null;
        }

        if (b.length < 32) {
            //There isn't even a complete header here!
            ilda.status.add("Invalid file");
            return null;
        }

        //Check if the first four bytes read ILDA:

        char[] theHeader = new char[4];
        for (int i = 0; i < 4; i++) {
            theHeader[i] = (char) b[i];
        }
        String hdr = new String(theHeader);
        if (!hdr.equals("ILDA")) {
            ilda.status.add("Error: file not an Ilda file. Loading cancelled.");
            ilda.status.add("Expected \"ILDA\", found \"" + hdr + "\"");
            return null;
        }

        //Retrieve where "ILDA" is found inside the file:

        framePositions = getFramePositions();
        //This actually returns the number of headers, and is normally one more than the real number of frames

        ilda.parent.println(framePositions);


        //This should never be true, because there was already a check if the file starts with an Ilda string
        if (framePositions == null) {
            ilda.status.add("No frames found.");
            return null;
        }

        IldaFrame frame;

        //If there is only one header, read until the end
        if (framePositions.size() == 1) {
            frame = readFrame(0, b.length - 1);
            if (frame != null) theFrames.add(frame);
        } else {
            //In case of multiple headers, read from current to next header
            //This is actually a bad way to do it because "ILDA" can occur by accident inside the data
            //Though as this chance is rare I decided to take the risk
            //And by "rare" I mean one in 72 quadrillion (1/256^7) for each byte
            //If you ever encounter such a frame, I'll send you a cookie
            for (int i = 1; i < framePositions.size(); i++) {
                //Skip to next ILDA occurence when ILDA occurs in the header
                if (framePositions.get(i) - framePositions.get(i - 1) <= 32 && (i + 1) < framePositions.size()) {
                    frame = readFrame(framePositions.get(i - 1), framePositions.get(i + 1));
                    if (frame != null) theFrames.add(frame);
                } else {
                    //Read the frame between this and the next header
                    frame = readFrame(framePositions.get(i - 1), framePositions.get(i));
                    if (frame != null) theFrames.add(frame);
                }
            }
        }
        ilda.status.add("Read " + theFrames.size() + " frames.");
        return theFrames;
    }

    public ArrayList<Integer> getFramePositions() {
        ArrayList<Integer> positions = new ArrayList();
        for (int j = 0; j < b.length - 6; j++) {
            if ((char) b[j] == 'I' && (char) b[j + 1] == 'L' && (char) b[j + 2] == 'D' && (char) b[j + 3] == 'A' && b[j + 4] == 0 && b[j + 5] == 0 && b[j + 5] == 0) {
                positions.add(j);
            }
        }
        return positions;
    }

    public IldaFrame readFrame(int offset, int end) {

        if (offset > end - 32) {
            ilda.status.add("Invalid frame");
            return null;
        }
        if (offset >= b.length - 32) {
            return null;
        }

        if (end >= b.length) {
            return null;
        }

        //Check if it does read ILDA (if it doesn't, check getFramePositions() ):
        char[] theHeader = new char[4];
        for (int i = 0; i < 4; i++) {
            theHeader[i] = (char) b[i];
        }
        String hdr = new String(theHeader);
        if (!hdr.equals("ILDA")) {
            ilda.status.add("Error: file not an Ilda file. Loading cancelled.");
            ilda.status.add("Expected \"ILDA\", found \"" + hdr + "\"");
            return null;
        }

        //Read header data:

        //Bytes 8-15: frame name
        char[] name = new char[8];
        for (int i = 0; i < 8; i++) {
            name[i] = (char) b[i + 8 + offset];
        }

        //Bytes 16-23: company name
        char[] company = new char[8];
        for (int i = 0; i < 8; i++) {
            company[i] = (char) b[i + 16 + offset];
        }

        //Bytes 24-25: point count in frames or total colours in palettes
        byte[] pointCountt = new byte[2];
        pointCountt[0] = b[24 + offset];
        pointCountt[1] = b[25 + offset];

        //Bytes 26-27: frame number in frames or palette number in palettes
        byte[] frameNumber = new byte[2];
        frameNumber[0] = b[26 + offset];
        frameNumber[1] = b[27 + offset];

        int ildaVersion = b[7 + offset];

        //Unsupported format detection:
        if (ildaVersion != 0 && ildaVersion != 1 && ildaVersion != 2 && ildaVersion != 4 && ildaVersion != 5) {
            ilda.status.add("Unsupported file format: " + ildaVersion);
            return null;
        }

        //Is this a palette or a frame? 2 = palette, rest = frame
        if (ildaVersion == 2) {
            ilda.status.add("Palette included");
            palette = new IldaPalette();

            palette.name = new String(name);
            palette.companyName = new String(company);
            palette.totalColors = unsignedShortToInt(pointCountt);

            //Byte 30: scanner head.
            palette.scannerHead = (int) b[30 + offset];


            // ILDA V2: Palette information

            for (int i = 32 + offset; i < end; i += 3) {
                palette.addColour((int) b[i], (int) b[i + 1], (int) b[i + 2]);
            }


            return null;
        } else {
            IldaFrame frame = new IldaFrame();  //Frame(this);      <- remains here as a symbol of how not to program

            //Byte 7 = Ilda file version
            frame.ildaVersion = ildaVersion;

            //Information previously read out (because palettes also have them):
            frame.frameName = new String(name);
            frame.companyName = new String(company);
            frame.pointCount = unsignedShortToInt(pointCountt);
            frame.frameNumber = unsignedShortToInt(frameNumber);


            //Bytes 28-29: total number of frames (not used in palettes)
            byte[] numberOfFrames = new byte[2];
            numberOfFrames[0] = b[28 + offset];
            numberOfFrames[1] = b[29 + offset];
            frame.totalFrames = unsignedShortToInt(numberOfFrames);

            //Byte 30: scanner head.
            frame.scannerHead = (int) b[30 + offset];


            // Read the points:

            // ILDA V0 (3D, Palettes)
            if (b[7 + offset] == 0) {
                frame.palette = true;
                for (int i = 32 + offset; i < end; i += 8) {
                    if (!(i >= b.length - 8)) {
                        byte[] x = new byte[2];
                        x[0] = b[i];
                        x[1] = b[i + 1];
                        short X = (short) unsignedShortToInt(x);
                        byte[] y = new byte[2];
                        y[0] = b[i + 2];
                        y[1] = b[i + 3];
                        short Y = (short) unsignedShortToInt(y);
                        byte[] z = new byte[2];
                        z[0] = b[i + 4];
                        z[1] = b[i + 5];
                        short Z = (short) unsignedShortToInt(z);

                        boolean bl = false;
                        if (b[i + 6] >> 7 == 1) bl = true;   //01000000 = 64 = 0x40

                        IldaPoint point = new IldaPoint(X, Y, Z, b[i + 7], bl);
                        frame.points.add(point);
                    }
                }

                frame.palettePaint(palette);
            }


            // ILDA V1: 2D, Palettes
            if (b[7 + offset] == 1) {
                frame.palette = true;
                for (int i = 32 + offset; i < end; i += 6) {
                    if (!(i >= b.length - 6)) {
                        byte[] x = new byte[2];
                        x[0] = b[i];
                        x[1] = b[i + 1];
                        short X = (short) unsignedShortToInt(x);
                        byte[] y = new byte[2];
                        y[0] = b[i + 2];
                        y[1] = b[i + 3];
                        short Y = (short) unsignedShortToInt(y);


                        boolean bl = false;
                        if (b[i + 4] >> 7 == 1) bl = true;


                        IldaPoint point = new IldaPoint(X, Y, 0, b[i + 5], bl);
                        frame.points.add(point);
                    }
                }

                frame.palettePaint(palette);
            }

            // ILDA V4: 3D, BGR
            if (b[7 + offset] == 4) {
                for (int i = 32 + offset; i < end; i += 10) {
                    if (!(i >= b.length - 10)) {
                        byte[] x = new byte[2];
                        x[0] = b[i];
                        x[1] = b[i + 1];
                        short X = (short) unsignedShortToInt(x);
                        byte[] y = new byte[2];
                        y[0] = b[i + 2];
                        y[1] = b[i + 3];
                        short Y = (short) unsignedShortToInt(y);
                        byte[] z = new byte[2];
                        z[0] = b[i + 4];
                        z[1] = b[i + 5];
                        short Z = (short) unsignedShortToInt(z);

                        boolean bl = false;
                        if (b[i + 6] >> 7 == 1) bl = true;   //01000000 = 64 = 0x40

                        IldaPoint point = new IldaPoint(X, Y, Z, b[i + 9], b[i + 8], b[i + 7], bl);
                        frame.points.add(point);
                    }
                }
            }

            //ILDA V5: 2D, BGR values
            //Why not RGB? Because reasons
            if (b[7 + offset] == 5) {
                for (int i = 32 + offset; i < end; i += 8) {
                    if (!(i >= b.length - 8)) {
                        byte[] x = new byte[2];
                        x[0] = b[i];
                        x[1] = b[i + 1];
                        short X = (short) unsignedShortToInt(x);
                        byte[] y = new byte[2];
                        y[0] = b[i + 2];
                        y[1] = b[i + 3];
                        short Y = (short) unsignedShortToInt(y);

                        boolean bl = false;
                        if (b[i + 4] >> 7 == 1) bl = true;

                        IldaPoint point = new IldaPoint(X, Y, 0, b[i + 7], b[i + 6], b[i + 5], bl);
                        frame.points.add(point);
                    }
                }
            }

            return frame;
        }
    }

    public final int unsignedShortToInt(byte[] b) {


        if (b.length != 2) {
            throw new IllegalArgumentException();
        }
        int i = 0;
        i |= b[0];
        i <<= 8;
        i |= b[1] & 0xff;
        return i;
    }
}

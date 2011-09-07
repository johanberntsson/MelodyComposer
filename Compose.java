import java.io.File;
import java.io.IOException;

import java.util.Random;
import java.util.HashMap;

import javax.sound.midi.Track;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Transmitter;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MetaEventListener;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;

public class Compose {
    public Compose() {
        rnd = new Random();
    }

    private Random rnd;
    private static final int VELOCITY = 64;

    static HashMap<String, Integer> notevalues = new HashMap<String, Integer>() {{
            put("C", 60); 
            put("D", 62);
            put("E", 64);
            put("F", 65);
            put("G", 67);
            put("A", 69);
            put("B", 71);
    }};

    private int[] parseChord(String chord) {
        int[] notes = new int[3+3+3];
        String base = chord.substring(0, 1);
        boolean minor = chord.length() == 2;
        notes[0] = notevalues.get(base) - 12;
        if(minor) {
            notes[1] = notes[0] + 3;
            notes[2] = notes[1] + 4;
        } else {
            notes[1] = notes[0] + 4;
            notes[2] = notes[1] + 3;
        }
        int c = 3;
        for(int j=0; j<2; j++) {
            for(int i=0; i<3; i++) {
                notes[c++] = notes[i]+j*12;
            }
        }
        /*
        for(int i=0; i<12; i++) {
            notes[c++] = 60+i;
        }
        */
        return notes;
    }

    int d = 1;
    int c = 4;
    int pickNote(int[] notes, double bias) {
        int r = rnd.nextInt(100);
        boolean keep = false;
        if(r < 5) {
            System.out.println("Changing");
            if(d==1) d = -1; else d = 1;
        } else if(r < 20) {
            System.out.println("keep");
            keep = true;
        } else if(r > 95) {
            System.out.println("random note");
            return 60+rnd.nextInt(12);
        }
        int chordrange = 9;
        if(!keep) c += d;
        if(c < 0) c = chordrange -1;
        if(c >= chordrange) c = 0;
        return notes[c];
    }
    int pickNote2(int[] notes, double bias) {
        // bias = 0: completely random
        // bias > 0: weighted random with bias toward the first notes
        double[] weight = new double[notes.length];
        for(int i=0; i<notes.length; i++) {
            weight[i] = 1 + bias * (notes.length - i);
        }

        double sum=0;
        for(int i=0;i<weight.length;sum+=weight[i++]);
        double rand=rnd.nextDouble() * sum; // 0 <= rand < sum
        int pick;
        double k;
        for(k=0,pick=0;k<=rand;k+=weight[pick++]);
        return notes[pick -1];
    }


    private MidiEvent createNoteOnEvent(int nKey, long lTick) {
        return createNoteEvent(ShortMessage.NOTE_ON, nKey, VELOCITY, lTick);
    }

    private MidiEvent createNoteOffEvent(int nKey, long lTick) {
        return createNoteEvent(ShortMessage.NOTE_OFF, nKey, 0, lTick);
    }

    private MidiEvent createNoteEvent( int nCommand, int nKey, int nVelocity, long lTick) {
        ShortMessage    message = new ShortMessage();
        try {
            message.setMessage(nCommand, 0,   // always on channel 1
                    nKey, nVelocity);
        } catch (InvalidMidiDataException e) {
            e.printStackTrace();
            System.exit(1);
        }
        MidiEvent   event = new MidiEvent(message, lTick);
        return event;
    }

    private Sequence readSong(String filename) {
        Sequence song = null;
        try {
            File midiFile = new File(filename);
            song = MidiSystem.getSequence(midiFile);
        } catch (InvalidMidiDataException e) {
            System.out.println("midi error: "+e.getMessage());
        } catch (IOException e) {
            System.out.println("Can't read the midi file: "+e.getMessage());
        }
        return song;
    }

    private Sequence makeSong(float divisiontype, int resolution, 
            Track melody, String[] chords ) {
        Sequence newsong = null;
        try {
            newsong = new Sequence(divisiontype, resolution);
        } catch (InvalidMidiDataException e) {
            System.out.println("sequence creation error: "+e.getMessage());
            return null;
        }

        /* create a new track */
        int maxBars = 30;
        int note;
        HashMap<Integer,Integer> newnotes = new HashMap<Integer,Integer>();
        Track newtrack = newsong.createTrack();
        for(int i=0; i<melody.size(); i++) {
            MidiEvent e = melody.get(i);
            MidiMessage m = e.getMessage();
            int bar = (int) (e.getTick()/resolution);
            System.out.println(bar);


            if(m instanceof ShortMessage) {
                if(bar < maxBars) {
                ShortMessage s = (ShortMessage) m;
                switch(s.getCommand()) {
                    case ShortMessage.PROGRAM_CHANGE:
                        //System.out.println(e.getTick()+" ON ");
                        break;
                    case ShortMessage.CONTROL_CHANGE:
                        //System.out.println(e.getTick()+" ON ");
                        break;
                    case ShortMessage.PITCH_BEND:
                        //System.out.println(e.getTick()+" ON ");
                        break;
                    case ShortMessage.NOTE_ON:
                        String chord = chords[bar%chords.length];
                        int[] notes = parseChord(chord);
                        // pick a random note in the chord
                        //note = pickNote(notes, 0.01);
                        note = pickNote(notes, 0);
                        //note = notes[rnd.nextInt(notes.length)];

                        // save the old-new tone mapping
                        // (so we know which note to stop in NOTE_OFF)
                        newnotes.put(s.getData1(), note);

                        //System.out.println(e.getTick()+" ON "+s.getData1());
                        newtrack.add(createNoteOnEvent(note, e.getTick()));
                        break;
                    case ShortMessage.NOTE_OFF:
                        note = newnotes.get(s.getData1());
                        newtrack.add(createNoteOffEvent(note, e.getTick()));
                        //System.out.println(e.getTick()+" OFF ");
                        break;
                    default:
                        System.out.println(e.getTick()+" "+s.getCommand());
                }
                }
            } else {
                System.out.println("long message "+m.getClass());
                e.setTick(maxBars*resolution);
                newtrack.add(e);
            }
        }

        return newsong;
    }

    private void saveSong(Sequence song, String filename) {
        // save the new song
        // The '0' (second parameter) means saving as SMF type 0.
        // Since we have only one track, this is actually the only option
        // (type 1 is for multiple tracks).
        try {
            File outputFile = new File(filename);
            MidiSystem.write(song, 0, outputFile);
        } catch (IOException e) {
            System.out.println("Failed to write the new file");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void playSong(Sequence song) {
        Sequencer sm_sequencer = null;
        Synthesizer sm_synthesizer = null;

        try {
            sm_sequencer = MidiSystem.getSequencer();
            sm_sequencer.open();
            sm_sequencer.setSequence(song);
        } catch (InvalidMidiDataException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        } catch (MidiUnavailableException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }

        if(!(sm_sequencer instanceof Synthesizer)) {
            // get the default synthesizer
            try {
                sm_synthesizer = MidiSystem.getSynthesizer();
                sm_synthesizer.open();
                Receiver    synthReceiver = sm_synthesizer.getReceiver();
                Transmitter seqTransmitter = sm_sequencer.getTransmitter();
                seqTransmitter.setReceiver(synthReceiver);
            } catch (MidiUnavailableException e) {
                System.out.println(e.getMessage());
                System.exit(1);
            }
        }
        MetaEventListener listener = new MetaEventListener() {
            public void meta(MetaMessage event) {
                if (event.getType() == 47) {
                    System.exit(0);
                }
            }
        };

        sm_sequencer.addMetaEventListener(listener);
        sm_sequencer.start();
    }

    private void compose(String midifilename, String[] chords) {
        Sequence originalsong = readSong(midifilename);
        if(originalsong == null) return;
        float divisiontype = originalsong.getDivisionType();
        int resolution = originalsong.getResolution();
        Track[] tracks = originalsong.getTracks();
        Track melody = tracks[0];

        Sequence newsong = makeSong(divisiontype, resolution, melody, chords);
        if(newsong == null) return;

        saveSong(newsong, "newsong.mid");

        playSong(newsong);
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("java Compose <midifile> <chord:chord:...>");
            System.out.println("Valid chords: C, Em, etc.");
            System.exit(1);
        }

        String[] chords = args[1].split(":");

        Compose c = new Compose();
        c.compose(args[0], chords);
        

    }
}


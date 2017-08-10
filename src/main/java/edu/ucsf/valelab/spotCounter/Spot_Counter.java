///////////////////////////////////////////////////////////////////////////////
//FILE:          Spot_Counter.java
//PROJECT:       SpotCounter
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.


package edu.ucsf.valelab.spotCounter;

import edu.ucsf.valelab.spotCounter.algorithm.FindLocalMaxima;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.NonBlockingGenericDialog;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.prefs.Preferences;

/**
 * Simple ImageJ plugin written to facilitate Marvin Tanenbaum's project.
 * This plugin finds local maxima, and outputs the number of local maxima
 * found, as well as the average intensity of all "spots" per frame
 *
 */
public class Spot_Counter implements 
        ExtendedPlugInFilter, DialogListener, ij.ImageListener, ClipboardOwner
{ 
   private static final int FLAGS = DOES_STACKS + DOES_8G + DOES_16 + NO_CHANGES;
   private static final String VERSION = "0.13";
   private final String BOXSIZEKEY = "BoxSize";
   private static int boxSize_ = 6;
   private final String NOISETOLERANCEKEY = "NoiseTolerance";
   private static int noiseTolerance_ = 1000;
   private final String CHECKSETTINGSKEY = "CheckSettings";
   private static boolean checkSettings_ = false;
   private final String OUTPUTTOCLIPBOARDKEY = "OutputToClipboard";
   private static boolean outputToClipboard_;
   private final String OUTPUTALLSPOTSKEY = "OutputAllSpots";
   private static boolean outputAllSpots_ = false;
   private final String APPENDOUTPUT = "AppendOutput";
   private static boolean appendOutput_ = false;
   private static FindLocalMaxima.FilterType filter_ = 
           FindLocalMaxima.FilterType.NONE;
   
   private Preferences prefs_;
   private int nPasses_ = 0;
   private int pasN_ = 0;
   private ImagePlus imgPlus_;
   private GenericDialog gd_;
   private String preFilterChoice_ = "";
   private boolean start_ = true;
   private ResultsTable res_;
   private ResultsTable res2_;

   @Override
   public int showDialog(ImagePlus ip, String string, PlugInFilterRunner pifr) {
      // this should be in constructor, but not sure what ImageJ does with constructor
      prefs_ = Preferences.userNodeForPackage(Spot_Counter.class);
      
      imgPlus_ = ip;
      gd_ = new NonBlockingGenericDialog("Spot Counter v." + VERSION);
      gd_.addHelp("http://imagej.net/SpotCounter");
      String prefilters[] = { 
         FindLocalMaxima.FilterType.NONE.toString(), 
         FindLocalMaxima.FilterType.GAUSSIAN1_5.toString() 
      };
      gd_.addChoice("Pre-filter: ", prefilters, filter_.toString() );
      boxSize_ = prefs_.getInt(BOXSIZEKEY, boxSize_);
      gd_.addNumericField("BoxSize: ", boxSize_, 0);
      noiseTolerance_ = prefs_.getInt(NOISETOLERANCEKEY, noiseTolerance_);
      gd_.addNumericField("Noise tolerance", noiseTolerance_, 0);
      checkSettings_ = prefs_.getBoolean(CHECKSETTINGSKEY, checkSettings_);
      gd_.addCheckbox("Check Settings", checkSettings_);
      outputToClipboard_ = prefs_.getBoolean(OUTPUTTOCLIPBOARDKEY, outputToClipboard_);
      gd_.addCheckbox("Output to Clipboard", outputToClipboard_);
      outputAllSpots_ = prefs_.getBoolean(OUTPUTALLSPOTSKEY, outputAllSpots_);
      gd_.addCheckbox("Output all spots", outputAllSpots_);
      appendOutput_ = prefs_.getBoolean(APPENDOUTPUT, appendOutput_);
      gd_.addCheckbox("Append new results", appendOutput_);
      gd_.addDialogListener(this);
      ImagePlus.addImageListener(this);
      
      // Note that showDialog is blocking! 
      // This particular thread will be sitting here until the user exits the dialog
      gd_.showDialog();

      
      return FLAGS; 
   }
   
   private void checkDialog() {
      preFilterChoice_ = gd_.getNextChoice();
      filter_ = FindLocalMaxima.FilterType.equals(preFilterChoice_);
      boxSize_ = (int) gd_.getNextNumber();
      noiseTolerance_ = (int) gd_.getNextNumber();
      checkSettings_ = ((Checkbox) gd_.getCheckboxes().get(0)).getState();
      outputToClipboard_ = ((Checkbox) gd_.getCheckboxes().get(1)).getState();
      outputAllSpots_ = ((Checkbox) gd_.getCheckboxes().get(2)).getState();
      appendOutput_ = ((Checkbox) gd_.getCheckboxes().get(3)).getState();
      
      // Store settings in preferences so that they can be restored
      prefs_.putInt(BOXSIZEKEY, boxSize_);
      prefs_.putInt(NOISETOLERANCEKEY, noiseTolerance_);
      prefs_.putBoolean(CHECKSETTINGSKEY, checkSettings_);
      prefs_.putBoolean(OUTPUTTOCLIPBOARDKEY, outputToClipboard_);
      prefs_.putBoolean(OUTPUTALLSPOTSKEY, outputAllSpots_);
      prefs_.putBoolean(APPENDOUTPUT, appendOutput_);
   }

   @Override
   public void setNPasses(int i) {
      nPasses_ = i;
      start_ = true;
   }

   @Override
   public int setup(String string, ImagePlus ip) {
      return FLAGS;
   }

   @Override
   public void run(ImageProcessor ip) {
      
      if (start_) {
         hideOverlay();
         ImagePlus.removeImageListener(this);
      }
      
      if (gd_.wasCanceled()) {
         start_ = false;
         return;
      }
      
      if (gd_.wasOKed()) {
         Overlay ov = getSpotOverlay(ip);
         if (start_) {
            if (appendOutput_) {
               res_ = ResultsTable.getResultsTable();
            }
            if (res_ == null) {
               res_ = new ResultsTable();
            }
            if (outputAllSpots_) {
               res2_ = new ResultsTable();
            }
            res_.setPrecision(1);
            pasN_ = 0;
            start_ = false;
         }
         res_.incrementCounter();
         if (appendOutput_) {
            res_.addValue("File", imgPlus_.getTitle());
         }
         pasN_ +=1;
         res_.addValue("n", ov.size());
         if (ov.size() > 0) {
            double sumRoiIntensities = 0;
            Rectangle originalRoi = ip.getRoi();
            for (int i = 0; i < ov.size(); i++) {
               Roi roi = ov.get(i);
               ip.setRoi(roi);
               sumRoiIntensities += ip.getStatistics().mean;
               if (outputAllSpots_) {
                  res2_.incrementCounter();
                  res2_.addValue("Image #", pasN_);
                  res2_.addValue("Spot Intensity", ip.getStatistics().mean);
               }
            }
            ip.setRoi(originalRoi);
            double mean = sumRoiIntensities / ov.size();
            res_.addValue("Spot mean", mean);
         }
         res_.addValue("Image mean", ip.getStatistics().mean);
         
         if (pasN_ == nPasses_) {
            if (appendOutput_) {
               res_.show("Results");
            } else {
               res_.show("Results for " + imgPlus_.getTitle());
            }
            if (outputAllSpots_) {
               res2_.show("All spots for " + imgPlus_.getTitle());
            }
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            StringBuilder output = new StringBuilder();
            // difficult way to get size of the resultstable to stay compatible
            // with older ij.jar versions that do not have res_.size()
            int size = res_.getColumn(0).length;
            for (int i = 0; i < size; i++) {
               if (appendOutput_) {
                  output.append(imgPlus_.getTitle()).append("\t");
               }
               output.append( (i + 1) ).append("\t"). 
                      append(res_.getValue("n", i)).append("\t"). 
                      append(res_.getValue("Spot mean", i)).append("\t").
                      append(res_.getValue("Image mean", i)).append("\n");
            }
            clipboard.setContents(new StringSelection(output.toString()), this);
         }
      }  
      
   }

   @Override
   public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
      checkDialog();
      if (checkSettings_) {
         Overlay ov = getSpotOverlay(imgPlus_.getProcessor());
         imgPlus_.setOverlay(ov);
      } else {
         hideOverlay();
      }
      return true;   
   }
   
   /**
    * Weird that all of this is needed to hide the overlay!   
   */
   private void hideOverlay() {
      Roi roi = new Roi(1, 1, imgPlus_.getWidth() - 2, imgPlus_.getHeight() - 2);
      roi.setStrokeColor(Color.YELLOW);
      Overlay ov = new Overlay(roi);
      imgPlus_.setOverlay(ov);
      imgPlus_.setHideOverlay(true);
   }

   /**
    * Finds local maxima and returns them as an collection of Rois (an overlay)
    * @param ip - ImageProcessor to be analyzed
    * @return overlay with local maxima
    */
   private Overlay getSpotOverlay(ImageProcessor ip) {
      Polygon pol = FindLocalMaxima.FindMax(
              ip, boxSize_, noiseTolerance_, filter_);
      int halfSize = boxSize_ / 2;
      Overlay ov = new Overlay();
      Roi imageRoi = ij.IJ.getImage().getRoi();
      ImageProcessor mask = null;
      if (imageRoi != null) {
         mask = imageRoi.getMask();
      }
      for (int i = 0; i < pol.npoints; i++) {
         int x = pol.xpoints[i];
         int y = pol.ypoints[i];
         boolean use = true;
         if (mask != null && ip.getRoi() != null) {
            if (mask.get(x - ip.getRoi().x, y - ip.getRoi().y) == 0) {
               use = false;
            }
         }
         if (use) {
            Roi roi = new Roi(x - halfSize, y - halfSize, boxSize_, boxSize_);
            roi.setStrokeColor(Color.RED);
            ov.add(roi);
         }
      }
      return ov;
   }

   @Override
   public void imageOpened(ImagePlus imp) {
      }

   @Override
   public void imageClosed(ImagePlus imp) {
      }

   @Override
   public void imageUpdated(ImagePlus imp) {
      // Only respond when this is our image:
      if (imp != imgPlus_)
         return;
      if (checkSettings_) {
         Overlay ov = getSpotOverlay(imgPlus_.getProcessor());
         imgPlus_.setOverlay(ov);
      }
   }

   @Override
   public void lostOwnership(Clipboard clpbrd, Transferable t) {
         // Nice!  Someone used our clipboard content
   }

}
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

/**
 * Simple ImageJ plugin written to facilitate Marvin Tananbaum's project.
 * This plugin finds local maxima, and outputs the number of local maxima
 * found, as well as the average intensity of all "spots" per frame
 *
 */
public class Spot_Counter implements 
        ExtendedPlugInFilter, DialogListener, ij.ImageListener
{ 
   private static final int flags_ = DOES_STACKS + DOES_8G + DOES_16 + NO_CHANGES; 
   private static int boxSize_ = 6;
   private static int noiseTolerance_ = 1000;
   private static boolean checkSettings_ = false;
   private static FindLocalMaxima.FilterType filter_ = 
           FindLocalMaxima.FilterType.NONE;
   
   private int nPasses_ = 0;
   private int pasN_ = 0;
   private ImagePlus imgPlus_;
   private GenericDialog gd_;
   private String preFilterChoice_ = "";
   private boolean start_ = true;
   private ResultsTable res_;

   @Override
   public int showDialog(ImagePlus ip, String string, PlugInFilterRunner pifr) {
      imgPlus_ = ip;
      gd_ = new NonBlockingGenericDialog("Spot Counter");
      String prefilters[] = { 
         FindLocalMaxima.FilterType.NONE.toString(), 
         FindLocalMaxima.FilterType.GAUSSIAN1_5.toString() 
      };
      gd_.addChoice("Pre-filter: ", prefilters, filter_.toString() );
      gd_.addNumericField("BoxSize: ", boxSize_, 0);
      gd_.addNumericField("Noise tolerance", noiseTolerance_, 0);
      gd_.addCheckbox("Check Settings", checkSettings_);
      gd_.addDialogListener(this);
      ImagePlus.addImageListener(this);
      
      // Note that showDialog is blocking! 
      // This particular thread will be sitting here until the user exits the dialog
      gd_.showDialog();

      
      return flags_; 
   }
   
   private void checkDialog() {
      preFilterChoice_ = gd_.getNextChoice();
      boxSize_ = (int) gd_.getNextNumber();
      noiseTolerance_ = (int) gd_.getNextNumber();
      filter_ = FindLocalMaxima.FilterType.equals(preFilterChoice_);
      Checkbox cb = (Checkbox) gd_.getCheckboxes().get(0);
      checkSettings_ = cb.getState();
   }

   @Override
   public void setNPasses(int i) {
      nPasses_ = i;
      start_ = true;
   }

   @Override
   public int setup(String string, ImagePlus ip) {
      return flags_;
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
            res_ = new ResultsTable();
            res_.setPrecision(1);
            pasN_ = 0;
            start_ = false;
         }
         res_.incrementCounter();
         pasN_ +=1;
         res_.addValue("n", ov.size());
         if (ov.size() > 0) {
            double sumRoiIntensities = 0;
            Rectangle originalRoi = ip.getRoi();
            for (int i = 0; i < ov.size(); i++) {
               Roi roi = ov.get(i);
               ip.setRoi(roi);
               sumRoiIntensities += ip.getStatistics().mean;
            }
            ip.setRoi(originalRoi);
            double mean = sumRoiIntensities / ov.size();
            res_.addValue("Spot mean", mean);
         }
         res_.addValue("Image mean", ip.getStatistics().mean);
         
         if (pasN_ == nPasses_)
            res_.show("Results for " + imgPlus_.getTitle());
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
    * Finds local maxima and returns them as an collaction of Rois (an overlay)
    * @param ip - ImageProcessor to be analyzed
    * @return overlay with local maxima
    */
   private Overlay getSpotOverlay(ImageProcessor ip) {
      Polygon pol = FindLocalMaxima.FindMax(
              ip, boxSize_, noiseTolerance_, filter_);
      int halfSize = boxSize_ / 2;
      Overlay ov = new Overlay();
      for (int i = 0; i < pol.npoints; i++) {
         int x = pol.xpoints[i];
         int y = pol.ypoints[i];
         Roi roi = new Roi(x - halfSize, y - halfSize, boxSize_, boxSize_);
         roi.setStrokeColor(Color.RED);
         ov.add(roi);
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

}
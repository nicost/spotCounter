package edu.ucsf.valelab.spotCounter;

import edu.ucsf.valelab.spotCounter.algorithm.FindLocalMaxima;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import java.awt.Polygon;

/**
 * 
 *
 */
public class Spot_Counter implements ExtendedPlugInFilter, DialogListener
{ 
   private final int flags_ = DOES_STACKS + DOES_8G + DOES_16; 
   private int nPasses_ = 0;
   private ImagePlus imgPlus_;
   private GenericDialog gd_;
   String preFilterChoice_ = "";
   int boxSize_ = 6;
   int noiseTolerance_ = 1000;
   FindLocalMaxima.FilterType filter_;

   @Override
   public int showDialog(ImagePlus ip, String string, PlugInFilterRunner pifr) {
      imgPlus_ = ip;
      gd_ = new GenericDialog("Spot Counter");
      String prefilters[] = {"None", "Gaussian1_5"};
      gd_.addChoice("Pre-filter: ", prefilters, "None");
      gd_.addNumericField("BoxSize: ", 6.0, 0);
      gd_.addNumericField("Noise tolerance", 1000.0, 0);
      gd_.addPreviewCheckbox(pifr, "Check Settings");
      gd_.addDialogListener(this);
      gd_.showDialog();
      
      return flags_; 
   }
   
   private void checkDialog() {
      preFilterChoice_ = gd_.getNextChoice();
      boxSize_ = (int) gd_.getNextNumber();
      noiseTolerance_ = (int) gd_.getNextNumber();
   }

   @Override
   public void setNPasses(int i) {
      IJ.log("# of passes set to " + i);
      nPasses_ = i;
   }

   @Override
   public int setup(String string, ImagePlus ip) {
      return flags_;
   }

   @Override
   public void run(ImageProcessor ip) {
      checkDialog();
      if (imgPlus_.getStack().getSize() > 1 && nPasses_ == 1) {
         // this is a preview, do not measure stuff
      } else 
      {
          Polygon pol = FindLocalMaxima.FindMax(
              imgPlus_.getProcessor(), boxSize_, noiseTolerance_, filter_);
          ResultsTable res = Analyzer.getResultsTable();
          res.incrementCounter();
          res.addValue("n", pol.npoints);
      }
      
   }

   @Override
   public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
      preFilterChoice_ = gd_.getNextChoice();
      boxSize_ = (int) gd_.getNextNumber();
      noiseTolerance_ = (int) gd_.getNextNumber();
      filter_ = FindLocalMaxima.FilterType.NONE;
      if (preFilterChoice_.equals("Gaussian1_5")) {
         filter_ = FindLocalMaxima.FilterType.GAUSSIAN1_5;
      } else {
         filter_ = FindLocalMaxima.FilterType.NONE;
      }
      if (gd_.getPreviewCheckbox().getState()) {
         showPreview();
      } else {
         Overlay ov = new Overlay();
         imgPlus_.setOverlay(ov);
         imgPlus_.setHideOverlay(true);
      }
      IJ.log("Dialog changed");
      return true;   
   }
   
   private void showPreview() {
      IJ.log("Running a preview");
      Polygon pol = FindLocalMaxima.FindMax(
              imgPlus_.getProcessor(), boxSize_, noiseTolerance_, filter_);
      int halfSize = boxSize_ / 2;
      Overlay ov = new Overlay();
      for (int i = 0; i < pol.npoints; i++) {
         int x = pol.xpoints[i];
         int y = pol.ypoints[i];
         ov.add(new Roi(x - halfSize, y - halfSize, 2 * halfSize, 2 * halfSize));
      }
      ov.add(new Roi(100, 100, 120, 120));
      imgPlus_.setOverlay(ov);
      imgPlus_.setHideOverlay(false);
   }

}
# spotCounter
Simple ImageJ/Fiji plugin to count spots in image stacks.  The plugin detects local maxima by scanning the image 
with a box of user-defined size.  Local maxima are accepted when the maximum is higher than a user-defined number
over the average of the 4 corners of the box.  The plugin outputs the number of spots per frame, the average 
intensity of all identified spots in a frame, and an estimate of the background intensity.  Data can optionally
be automatically copied to the System Cliboard.  This really is a simple plugin meant to facilitate the work-flow
of certain experiments in the lab.

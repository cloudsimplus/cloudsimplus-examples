# Text files containing Hosts power utilization data

Each file inside this directory represents the power consumption (in Watts)
for a Host with a specific model and manufactorer.
Such files are used by the PowerModelHostSpec class to determine
Host power consumption according to CPU utilization percentage.

For instance, the data for an `HP ProLiant ML110 G3 (1 x [Pentium D930 3000 MHz, 2 cores], 4GB)` Host, 
available [here](https://www.spec.org/power_ssj2008/results/res2011q1/power_ssj2008-20110127-00342.html), 
can be included into a text file to be read by the PowerModelHostSpec class.

The first line of the file should include the values from the `Power` column (shown in the sample link above), 
where each value is separated by a space. All remaining lines are ignored. 
Check PowerModelHostSpec.getInstance() for more details.

The content of these files are based on public data available at https://www.spec.org 
and were extracted from CloudSim 3.0.3 PowerModelHostSpec subclasses.
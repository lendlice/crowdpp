/*
	Copyright (c) 2011
		Speech Group at Informatik 5, Univ. Erlangen-Nuremberg, GERMANY
		Korbinian Riedhammer
		Tobias Bocklet

	This file is part of the Java Speech Toolkit (JSTK).

	The JSTK is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	The JSTK is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with the JSTK. If not, see <http://www.gnu.org/licenses/>.
*/
package de.fau.cs.jstk.stat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import de.fau.cs.jstk.io.FrameInputStream;
import de.fau.cs.jstk.io.IOUtil;
import de.fau.cs.jstk.stat.Density.Flags;
import de.fau.cs.jstk.stat.MmieAccumulator.MmieOptions;
import de.fau.cs.jstk.util.Pair;

public final class MleMixtureAccumulator {
	private static Logger logger = Logger.getLogger(MleMixtureAccumulator.class);
	
	private Class<? extends Density> host;
	
	private int fd, nd;
	
	public MleDensityAccumulator [] accs;
	
	public MleMixtureAccumulator(int fd, int nd, Class<? extends Density> host) 
			throws ClassNotFoundException {
		this.fd = fd;
		this.nd = nd;
		this.host = host;
		
		// verify that we support that host density
		if (!(host.equals(DensityDiagonal.class) || host.equals(DensityFull.class)))
			throw new ClassNotFoundException("MleMixtureAccumulator not implemented for " + host.toString());
		
		// allocate accumulators
		accs = new MleDensityAccumulator [nd];
		for (int i = 0; i < nd; ++i)
			accs[i] = new MleDensityAccumulator(fd, host);
	}
	
	public MleMixtureAccumulator(MleMixtureAccumulator copy) {
		this.fd = copy.fd;
		this.nd = copy.nd;
		this.host = copy.host;
		
		accs = new MleDensityAccumulator [nd];
		for (int i = 0; i < nd; ++i)
			accs[i] = new MleDensityAccumulator(copy.accs[i]);
	}	
	
	public MleMixtureAccumulator(InputStream is) throws IOException {
		read(is);
	}
	
	public void read(InputStream is) throws IOException {
		int htype = IOUtil.readInt(is, ByteOrder.LITTLE_ENDIAN);
		if (htype == MleDensityAccumulator.HOST_DIAGONAL)
			host = DensityDiagonal.class;
		else if (htype == MleDensityAccumulator.HOST_FULL)
			host = DensityFull.class;
		else
			throw new IOException("Unknown host-type " + htype);
		
		fd = IOUtil.readInt(is, ByteOrder.LITTLE_ENDIAN);
		nd = IOUtil.readInt(is, ByteOrder.LITTLE_ENDIAN);
		
		accs = new MleDensityAccumulator [nd];
		for (int i = 0; i < nd; ++i)
			accs[i] = new MleDensityAccumulator(is);
	}
	
	public void write(OutputStream os) throws IOException {
		if (host.equals(DensityDiagonal.class))
			IOUtil.writeInt(os, MleDensityAccumulator.HOST_DIAGONAL, ByteOrder.LITTLE_ENDIAN);
		else
			IOUtil.writeInt(os, MleDensityAccumulator.HOST_FULL, ByteOrder.LITTLE_ENDIAN);
		
		IOUtil.writeInt(os, fd, ByteOrder.LITTLE_ENDIAN);
		IOUtil.writeInt(os, nd, ByteOrder.LITTLE_ENDIAN);
		
		for (MleDensityAccumulator da : accs)
			da.write(os);
		
		os.flush();
	}
	
	public void accumulate(double gamma, double [] x, int i) {
		accs[i].accumulate(gamma, x);
	}
	
	public void accumulate(double [] gamma, double [] x) {
		for (int i = 0; i < nd; ++i)
			accs[i].accumulate(gamma[i], x);
	}
	
	public void propagate(MleMixtureAccumulator source) {
		if (fd != source.fd || nd != source.nd)
			throw new RuntimeException("Feature dim and/or number of densities mismatch!");
		
		for (int i = 0; i < nd; ++i)
			accs[i].propagate(source.accs[i]);
	}
	
	public void interpolate(MleMixtureAccumulator source, double weight) {
		if (fd != source.fd || nd != source.nd)
			throw new RuntimeException("Feature dim and/or number of densities mismatch!");
		
		for (int i = 0; i < nd; ++i)
			accs[i].interpolate(source.accs[i], weight);
	}
	
	public void flush() {
		for (MleDensityAccumulator a : accs)
			a.flush();
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		
		sb.append("MleMixtureAccumulator fd=" + fd + " nd=" + nd + "\n");
		for (MleDensityAccumulator d : accs)
			sb.append(d.toString());
		
		return sb.toString();
	}
	
	public static void MleUpdate(Mixture min, 
			MleDensityAccumulator.MleOptions opt, 
			Density.Flags flags, 
			MleMixtureAccumulator acc, Mixture mout) {
		
		// compute normalization for weights (sum of component occupancies)
		double norm = 0.0;
		for (int i = 0; i < min.nd; ++i)
			norm += acc.accs[i].occ;
		
		if (norm == 0.0) {
			logger.info("No occupancy logged; aborting reestimation");
			return;
		}
		
		for (int i = 0; i < min.nd; ++i)
			MleDensityAccumulator.MleUpdate(min.components[i], opt, flags, 
					acc.accs[i], acc.accs[i].occ / norm, mout.components[i]);
	}
	
	public static final String SYNOPSIS =
		"usage: stat.MleMixtureAccumulator [command] [arguments]\n" +
		"  acc   mixture-list prot-file data-in-out-list\n" +
		"  mle   mixture-old mixture-new acc-list\n" +
		"  mmie  mixture-in-out-list prot-file-with-labels [wmv]\n" +
		"  disp  accumulator1 [accumulator2 ...]\n";
	
	public static void main(String [] args) throws Exception {
		BasicConfigurator.configure();
		
		if (args.length < 1) {
			System.err.println(SYNOPSIS);
			System.exit(1);
		}
		
		if (args[0].equals("acc"))
			doacc(args);
		else if (args[0].equals("mle"))
			domle(args);
		else if (args[0].equals("mmie"))
			dommie(args);
		else if (args[0].equals("disp"))
			dodisp(args);
		else
			System.err.println(SYNOPSIS);
	}
	
	private static void doacc(String [] args) throws Exception {
		if (args.length != (3+1)) {
			System.err.println(SYNOPSIS);
			System.err.println("cmd-line was:" + Arrays.toString(args));
			System.exit(1);
		}
		
		// read mixtures
		List<Mixture> mixtures = new LinkedList<Mixture>();
		BufferedReader br = new BufferedReader(new FileReader(args[1]));
		String l;
		while ((l = br.readLine()) != null) {
			Mixture m = new Mixture(new FileInputStream(l));
			if (mixtures.size() > 0 && m.fd != mixtures.get(0).fd) {
				logger.error("Feature dimensions of Mixtures do not match!");
				System.exit(1);
			}
			
			mixtures.add(m);
		}
		
		if (mixtures.size() == 0) {
			logger.error("No mixtures loaded?!");
			System.exit(1);
		}
		
		double [] x = new double [mixtures.get(0).fd];
		
		logger.info("read " + mixtures.size() +  " mixtures");
		
		// open protocol file
		BufferedWriter prot = new BufferedWriter(new FileWriter(args[2]));
		
		// open in-out-list
		br = new BufferedReader(new FileReader(args[3]));
		int nfiles = 0;
		long nsamples = 0;
		double [] globllh = new double [mixtures.size()];
		while ((l = br.readLine()) != null) {
			String [] iof = l.trim().split("\\s+");
			if (iof.length != 2) {
				logger.error("skipping invalid line: " + l);
				continue;
			}
			
			// cache feature data
			FrameInputStream fis = new FrameInputStream(new File(iof[0]));
			List<double []> data = new LinkedList<double []>();
			while (fis.read(x))
				data.add(x.clone());
			
			logger.info(iof[0] + ": " + data.size() + " samples");
			
			// open file for accumulators
			FileOutputStream fos = new FileOutputStream(iof[1]);
			prot.write(iof[1] + " " + data.size());
			
			nfiles += 1;
			nsamples += data.size();
			
			// accumulate for all mixtures
			for (int i = 0; i < mixtures.size(); ++i) {
				Mixture m = mixtures.get(i);
				MleMixtureAccumulator a = new MleMixtureAccumulator(m.fd, m.nd, m.diagonal ? DensityDiagonal.class : DensityFull.class);
				
				double llh = 0.0;
				double [] p = new double [m.nd];
				
				for (double [] xx : data) {
					m.evaluate(xx);
					m.posteriors(p);
					a.accumulate(p, xx);
					
					llh += m.logscore;
				}
				
				globllh[i] += llh;
				
				a.write(fos);
				prot.write(" " + llh);
				logger.info(i + " " + iof[0] + " average-logscore = " + (llh/data.size()));
			}
			
			fos.close();
			prot.write("\n");
			prot.flush();
		}
		
		prot.close();
		
		for (int i = 0; i < globllh.length; ++i)
			globllh[i] /= nsamples;
		logger.info("Finished accumulating " + nfiles + " files (" + nsamples + " samples) average logscores = " + Arrays.toString(globllh));
	}
	
	private static void domle(String [] args) throws Exception {
		
	}
	
	private static void dommie(String [] args) throws Exception {
		final double C = 2.0;
		
		if (args.length < (2+1)) {
			System.err.println("cmd-line was:" + Arrays.toString(args));
			System.err.println(SYNOPSIS);
			System.exit(1);
		}
		
		HashMap<Integer, Mixture> inventory = new HashMap<Integer, Mixture>();
		HashMap<Integer, Double> Ws = new HashMap<Integer, Double>();
		
		List<Pair<String, String>> iolist = new LinkedList<Pair<String, String>>();
		BufferedReader br = new BufferedReader(new FileReader(args[1]));
		String l;
		int j = 0;
		while((l = br.readLine()) != null) {
			String [] sl = l.trim().split("\\s+");
			iolist.add(new Pair<String, String>(sl[0], sl[1]));
			Ws.put(j++, Double.parseDouble(sl[2]));
		}
		br.close();
		
		// build MMIE Accumulator
		MmieAccumulator ma = new MmieAccumulator();
		
		// load mixtures
		for (int i = 0; i < iolist.size(); ++i) {
			Mixture m = new Mixture(new FileInputStream(iolist.get(i).a));
			ma.register(i, m, Ws.get(i));
			inventory.put(i, m);
		}
		
		logger.info("read " + inventory.size() + " mixtures");
		
		// read MLE accumulators
		BufferedReader pr = new BufferedReader(new FileReader(args[2]));
		j = 0;
		while ((l = pr.readLine()) != null) {
			String [] sl = l.trim().split("\\s+");
			int t = Integer.parseInt(sl[0]);
			
			// read accumulators
			HashMap<Integer, Double> logscores = new HashMap<Integer, Double>();
			HashMap<Integer, MleMixtureAccumulator> statistics = new HashMap<Integer, MleMixtureAccumulator>();
			
			FileInputStream fis = new FileInputStream(sl[1]);
			for (int i = 0; i < iolist.size(); ++i) {
				logscores.put(i, Double.parseDouble(sl[3+i]));
				statistics.put(i, new MleMixtureAccumulator(fis));
			}
			fis.close();
			
			long segl = Long.parseLong(sl[2]);
			
			ma.addSegment(t, logscores, statistics, C / segl);
			j++;
		}
		
		logger.info("read " + j + " MLE accumulators");
		
		Flags flags = Flags.fAllParams;
		if (args.length > 3) {
			String wmv = args[3].toLowerCase();
			boolean w = wmv.contains("w");
			boolean m = wmv.contains("m");
			boolean v = wmv.contains("v");
			
			flags = new Flags(w, m, v);
		}
		
		// reestimate the Mixtures
		HashMap<Integer, Mixture> invout = new HashMap<Integer, Mixture>();
		MmieAccumulator.MmieUpdate(inventory, MmieOptions.pDefaultOptions, flags, ma.statistics, invout);
		
		logger.info("writing out mixtures");
		for (int i = 0; i < iolist.size(); ++i) {
			Mixture m = invout.get(i);
			FileOutputStream fos = new FileOutputStream(iolist.get(i).b);
			m.write(fos);
			fos.flush();
			fos.close();
		}
	}
	
	private static void dodisp(String [] args) throws Exception {
		for (int i = 1; i < args.length; ++i) {
			FileInputStream fis = new FileInputStream(args[i]);
			try{
				while (true) {
					MleMixtureAccumulator a = new MleMixtureAccumulator(fis);
					System.out.println(a.toString());
				}
			} catch (Exception e) {
				
			}
		}
	}
}

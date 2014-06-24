/*
	Copyright (c) 2009-2011
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
package de.fau.cs.jstk.util;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * This class provides basic linear algebra routines used in the whole project
 * 
 * @author sikoried
 */
public final class Arithmetics {
	/**
	 * Compute the pseudo-inverse using SVD and regularize the inverses of the 
	 * singular values
	 * @param m matrix[rows][cols]\
	 * @param eps tolerance for inverse, e.g. 1E-12
	 * @return
	 */
	public static double [][] pinv(double [][] m, double eps) {
		int rows = m.length;
		if (rows < 1)
			throw new RuntimeException("Matrix contains no columns!");
		
		int cols = m[0].length;
		
		Jama.SingularValueDecomposition svd = new Jama.SingularValueDecomposition(new Jama.Matrix(m));
		double [] sv = svd.getSingularValues();
		
		// "invert" the singular values, discard values with "bad" numerics
		double tol = Math.max(rows, cols) * sv[0] * eps;
		for (int i = 0; i < sv.length; i++)
			sv[i] = Math.abs(sv[i]) < tol ? 0. : (1. / sv[i]);
		
		double [][] U = svd.getU().getArray();
		double [][] V = svd.getV().getArray();
		
		// pinv = U * S^-1 * V
		int min = Math.min(cols, U[0].length);
		double [][] pinv = new double [cols][rows];
		for (int i = 0; i < cols; i++)
			for (int j = 0; j < U.length; j++)
				for (int k = 0; k < min; k++)
					pinv[i][j] += V[i][k] * sv[k] * U[j][k];

		return pinv;
	}
	
	/**
	 * Compute the pseudo-inverse using SVD and regularize the inverses of the 
	 * singular values
	 * @param m matrix[rows][cols]\
	 * @param eps tolerance for inverse, e.g. 1E-12
	 * @return
	 */
	public static float [][] pinv(float [][] m, float eps) {
		int rows = m.length;
		if (rows < 1)
			throw new RuntimeException("Matrix contains no columns!");
		
		int cols = m[0].length;
		
		FJama.SingularValueDecomposition svd = new FJama.SingularValueDecomposition(new FJama.Matrix(m));
		float [] sv = svd.getSingularValues();
		
		// "invert" the singular values, discard values with "bad" numerics
		float tol = Math.max(rows, cols) * sv[0] * eps;
		for (int i = 0; i < sv.length; i++)
			sv[i] = ((float) Math.abs(sv[i])) < tol ? 0.f : (1.f / sv[i]);
		
		float [][] U = svd.getU().getArray();
		float [][] V = svd.getV().getArray();
		
		// pinv = U * S^-1 * V
		int min = Math.min(cols, U[0].length);
		float [][] pinv = new float [cols][rows];
		for (int i = 0; i < cols; i++)
			for (int j = 0; j < U.length; j++)
				for (int k = 0; k < min; k++)
					pinv[i][j] += V[i][k] * sv[k] * U[j][k];

		return pinv;
	}
	
	/**
	 * Interpolate the values as a[i] = wt * b[i] + (1. - wt) * a[i];
	 * @param a
	 * @param b
	 * @param wt weight of b
	 */
	public static void interp1(double [] a, double [] b, double wt) {
		double wti = 1. - wt;
		for (int i = 0; i < a.length; ++i)
			a[i] = wt * b[i] + wti * a[i];
	}
	
	/**
	 * Interpolate the values as ret[i] = wt * b[i] + (1. - wt) * a[i];
	 * @param a
	 * @param b
	 * @param wt weight of b
	 * @return interpolation result
	 */
	public static double [] interp2(double [] a, double [] b, double wt) {
		double wti = 1. - wt;
		double [] ret = new double [a.length];
		for (int i = 0; i < a.length; ++i)
			ret[i] = wt * b[i] + wti * a[i];
		
		return ret;
	}
	
	/**
	 * Interpolate the values as a[i] = wt * b[i] + (1. - wt) * a[i];
	 * @param a
	 * @param b
	 * @param wt weight of b
	 */
	public static void interp1(float [] a, float [] b, float wt) {
		float wti = 1.f - wt;
		for (int i = 0; i < a.length; ++i)
			a[i] = wt * b[i] + wti * a[i];
	}
	
	/**
	 * Interpolate the values as ret[i] = wt * b[i] + (1. - wt) * a[i];
	 * @param a
	 * @param b
	 * @param wt weight of b
	 * @return interpolation result
	 */
	public static float [] interp2(float [] a, float [] b, float wt) {
		float wti = 1.f - wt;
		float [] ret = new float [a.length];
		for (int i = 0; i < a.length; ++i)
			ret[i] = wt * b[i] + wti * a[i];
		
		return ret;
	}
	
	/**
	 * c = a + b
	 */
	public static double [] vadd1(double [] a, double [] b) {
		double [] res = new double [a.length];
		for (int i = 0; i < a.length; ++i)
			res[i] = a[i] + b[i];
		return res;
	}

	/**
	 * a += b
	 */
	public static void vadd2(double [] a, double [] b) {
		for (int i = 0; i < a.length; ++i)
			a[i] += b[i];
	}

	/**
	 * c = a + b where b is a scalar
	 */
	public static double [] vadd3(double [] a, double b) {
		double [] res = new double [a.length];
		for (int i = 0; i < a.length; ++i)
			res[i] = a[i] + b;
		return res;
	}

	/**
	 * a += b where b is a scalar
	 */
	public static void vadd4(double [] a, double b) {
		for (int i = 0; i < a.length; ++i)
			a[i] += b;
	}

	/**
	 * ret = a + c * b where c is scalar
	 */
	public static double [] vadd5(double [] a, double [] b, double c) {
		double [] ret = new double [a.length];
		for (int i = 0; i < a.length; ++i)
			ret[i] = a[i] + c * b[i];
		return ret;
	}
	

	/**
	 * a += c * b where c is scalar
	 */
	public static void vadd6(double [] a, double [] b, double c) {
		for (int i = 0; i < a.length; ++i)
			a[i] += c * b[i];
	}
	
	/**
	 * a += c * b * b' where c is scalar and a is an upper triangular matrix
	 */
	public static void vspaddsp(double [] a, double [] b, double c) {
		int m = 0;
		for (int k = 0; k < b.length; ++k)
			for (int l = 0; l <= k; ++l)
				a[m++] += c * b[k] * b[l];
	}
	
	/**
	 * c = a - b
	 */
	public static double [] vsub1(double [] a, double [] b) {
		double [] res = new double [a.length];
		for (int i = 0; i < a.length; ++i)
			res[i] = a[i] - b[i];
		return res;
	}

	/**
	 * a -= b
	 */
	public static void vsub2(double [] a, double [] b) {
		for (int i = 0; i < a.length; ++i)
			a[i] -= b[i];
	}

	/**
	 * c = a - b where b is a scalar
	 */
	public static double [] vsub3(double [] a, double b) {
		return vadd3(a, -b);
	}

	/**
	 * a -= b where b is a scalar
	 */
	public static void vsub4(double [] a, double b) {
		vadd4(a, -b);
	}

	/**
	 * c = dot-product(a, b)
	 */
	public static double dotp(double [] a, double [] b) {
		double res = 0.;
		for (int i = 0; i < a.length; ++i)
			res += a[i] * b[i];
		return res;
	}

	/**
	 * component wise multiplication c = a .* b
	 */
	public static double [] compmul1(double [] a, double [] b) {
		double [] res = new double [a.length];
		for (int i = 0; i < a.length; ++i)
			res[i] = a[i] * b[i];
		return res;
	}

	/**
	 * a *= b component wise
	 */
	public static void compmul2(double [] a, double [] b) {
		for (int i = 0; i < a.length; ++i)
			a[i] *= b[i];
	}

	/**
	 * component wise division c = a ./ b
	 */
	public static double [] compdiv1(double [] a, double [] b) {
		double [] res = new double [a.length];
		for (int i = 0; i < a.length; ++i)
			res[i] = a[i] / b[i];
		return res;
	}

	/**
	 * a /= b component wise
	 */
	public static void compdiv2(double [] a, double [] b) {
		for (int i = 0; i < a.length; ++i)
			a[i] /= b[i];
	}

	/**
	 * C = a^T a
	 */
	public static double [][] crossp(double [] a, double [] b) {
		double [][] res = new double [a.length] [b.length];
		for (int i = 0; i < a.length; ++i)
			for (int j = 0; j < b.length; ++j)
				res[i][j] = a[i] * b[j];
		return res;
	}

	/**
	 * c = a * b where b is a scalar
	 */
	public static double [] smul1(double [] a, double b) {
		double [] res = new double [a.length];
		for (int i = 0; i < a.length; ++i)
			res[i] = a[i] * b;
		return res;
	}

	/**
	 * a *= b where b is a scalar
	 */
	public static void smul2(double [] a, double b) {
		for (int i = 0; i < a.length; ++i)
			a[i] *= b;
	}

	/**
	 * c = a / b where b is a scalar
	 */
	public static double [] sdiv1(double [] a, double b) {
		double [] res = new double [a.length];
		for (int i = 0; i < a.length; ++i)
			res[i] = a[i] / b;
		return res;
	}

	/**
	 * a /= b where b is a scalar
	 */
	public static void sdiv2(double [] a, double b) {
		for (int i = 0; i < a.length; ++i)
			a[i] /= b;
	}

	/**
	 * |v|
	 */
	public static double norm1(double [] v) {
		double r = 0;
		for (double d : v)
			r += Math.abs(d);
		return r;
	}

	/**
	 * ||v||^2
	 */
	public static double norm2(double [] v) {
		double r = 0;
		for (double d : v)
			r += d * d;
		return Math.sqrt(r);
	}

	/**
	 * c = a + b
	 */
	public static float [] vadd1(float [] a, float [] b) {
		float [] res = new float [a.length];
		for (int i = 0; i < a.length; ++i)
			res[i] = a[i] + b[i];
		return res;
	}

	/**
	 * a += b
	 */
	public static void vadd2(float [] a, float [] b) {
		for (int i = 0; i < a.length; ++i)
			a[i] += b[i];
	}

	/**
	 * c = a + b where b is a scalar
	 */
	public static float [] vadd3(float [] a, float b) {
		float [] res = new float [a.length];
		for (int i = 0; i < a.length; ++i)
			res[i] = a[i] + b;
		return res;
	}

	/**
	 * a += b where b is a scalar
	 */
	public static void vadd4(float [] a, float b) {
		for (int i = 0; i < a.length; ++i)
			a[i] += b;
	}

	/**
	 * c = a - b
	 */
	public static float [] vsub1(float [] a, float [] b) {
		float [] res = new float [a.length];
		for (int i = 0; i < a.length; ++i)
			res[i] = a[i] - b[i];
		return res;
	}

	/**
	 * a -= b
	 */
	public static void vsub2(float [] a, float [] b) {
		for (int i = 0; i < a.length; ++i)
			a[i] -= b[i];
	}

	/**
	 * c = a - b where b is a scalar
	 */
	public static float [] vsub3(float [] a, float b) {
		return vadd3(a, -b);
	}

	/**
	 * a -= b where b is a scalar
	 */
	public static void vsub4(float [] a, float b) {
		vadd4(a, -b);
	}

	/**
	 * c = dot-product(a, b)
	 */
	public static float dotp(float [] a, float [] b) {
		float res = 0.f;
		for (int i = 0; i < a.length; ++i)
			res += a[i] * b[i];
		return res;
	}

	/**
	 * component wise multiplication c = a .* b
	 */
	public static float [] compmul1(float [] a, float [] b) {
		float [] res = new float [a.length];
		for (int i = 0; i < a.length; ++i)
			res[i] = a[i] * b[i];
		return res;
	}

	/**
	 * a *= b component wise
	 */
	public static void compmul2(float [] a, float [] b) {
		for (int i = 0; i < a.length; ++i)
			a[i] *= b[i];
	}

	/**
	 * component wise division c = a ./ b
	 */
	public static float [] compdiv1(float [] a, float [] b) {
		float [] res = new float [a.length];
		for (int i = 0; i < a.length; ++i)
			res[i] = a[i] / b[i];
		return res;
	}

	/**
	 * a /= b component wise
	 */
	public static void compdiv2(float [] a, float [] b) {
		for (int i = 0; i < a.length; ++i)
			a[i] /= b[i];
	}

	/**
	 * C = a^T a
	 */
	public static float [][] crossp(float [] a, float [] b) {
		float [][] res = new float [a.length] [b.length];
		for (int i = 0; i < a.length; ++i)
			for (int j = 0; j < b.length; ++j)
				res[i][j] = a[i] * b[j];
		return res;
	}

	/**
	 * c = a * b where b is a scalar
	 */
	public static float [] smul1(float [] a, float b) {
		float [] res = new float [a.length];
		for (int i = 0; i < a.length; ++i)
			res[i] = a[i] * b;
		return res;
	}

	/**
	 * a *= b where b is a scalar
	 */
	public static void smul2(float [] a, float b) {
		for (int i = 0; i < a.length; ++i)
			a[i] *= b;
	}

	/**
	 * c = a / b where b is a scalar
	 */
	public static float [] sdiv1(float [] a, float b) {
		float [] res = new float [a.length];
		for (int i = 0; i < a.length; ++i)
			res[i] = a[i] / b;
		return res;
	}

	/**
	 * a /= b where b is a scalar
	 */
	public static void sdiv2(float [] a, float b) {
		for (int i = 0; i < a.length; ++i)
			a[i] /= b;
	}

	/**
	 * |v|
	 */
	public static float norm1(float [] v) {
		float r = 0;
		for (float d : v)
			r += Math.abs(d);
		return r;
	}

	/**
	 * ||v||^2
	 */
	public static float norm2(float [] v) {
		float r = 0.f;
		for (float d : v)
			r += d * d;
		return (float) Math.sqrt(r);
	}
	
	/**
	 * || a + s*b ||^2
	 * @param s
	 * @param a
	 * @param b
	 * @return
	 */
	public static double norm2(double s, double [] a, double [] b) {
		double r = 0.;
		for (int i = 0; i < a.length; ++i) {
			double p = (a[i] + s * b[i]);
			r += p*p;
		}
		return Math.sqrt(r);
	}
	
	/**
	 * Enforce sum_i a[i] = 1
	 * @param a
	 */
	public static void makesumto1(float [] a) {
		float s = 0.f;
		for (int i = 0; i < a.length; ++i)
			s += a[i];
		for (int i = 0; i < a.length; ++i)
			a[i] /= s;
	}
	
	/**
	 * Enforce sum_i a[i] = 1
	 * @param a
	 */
	public static void makesumto1(double [] a) {
		double s = 0.;
		for (int i = 0; i < a.length; ++i)
			s += a[i];
		for (int i = 0; i < a.length; ++i)
			a[i] /= s;
	}
	
	public static double min(double [] a) {
		double m = a[0];
		for (int i = 1; i < a.length; ++i)
			m = Math.min(m, a[i]);
		return m;
	}
	
	public static double max(double [] a) {
		double m = a[0];
		for (int i = 1; i < a.length; ++i)
			m = Math.max(m, a[i]);
		return m;
	}
	
	/** Get the minimum of the trace of a upper triangular matrix */
	public static double minsp(double [] a, int d) {
		double m = a[0];
		
		for (int i = 1, j = d; i < d; ++i) {
			m = Math.min(m, a[j]);
			j += (d-i);
		}
		return m;
	}
	
	/** Get the m maximum of the trace of a upper triangular matrix */
	public static double maxsp(double [] a, int d) {
		double m = a[0];
		
		for (int i = 1, j = d; i < d; ++i) {
			m = Math.max(m, a[j]);
			j += (d-i);
		}
		return m;
	}
	
	/**
	 * Add a list of double values in a numerically stable way by adding always
	 * two consecutive values (which are expected to be of similar size), to
	 * avoid the floating-point issues that arise when adding numbers of
	 * different size
	 * @param vals
	 * @return
	 */
	public static double stableadd(LinkedList<Double> vals) {
		LinkedList<Double> li1 = vals;
		LinkedList<Double> li2 = new LinkedList<Double>();
		
		while (li1.size() > 1) {
			Iterator<Double> it = li1.iterator();
			
			for (int i = 0; i < li1.size() / 2; ++i) {
				double v1 = it.next();
				double v2 = it.next();
				
				li2.add(v1 + v2);
			}
			
			if (li1.size() % 2 == 1)
				li2.addFirst(it.next());
			
			li1.clear();
			LinkedList<Double> tmp = li1;
			li1 = li2;
			li2 = tmp;
		}
		
		return li1.get(0);
	}
}

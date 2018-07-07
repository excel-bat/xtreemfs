/*
 * Copyright (c) 2009-2011 by Juan Gonzalez de Benito, Jonathan Marti,
 *                            Barcelona Supercomputing Center
 *
 * Licensed under the BSD License, see LICENSE file for details.
 *
 */

package org.xtreemfs.osd.vivaldi;

import java.util.Random;

/**
 * zipf 法则
 *
 * 相信你一定听过这样的说法：
 * 80%的财富集中在20%的人手中……
 * 80%的用户只使用20%的功能……
 * 20%的用户贡献了80%的访问量……
 * …………
 * 你知道我在说“二八原则”或“20/80原则”，是的，没错！
 * -----------
 * 如果把所有的单词（字）放在一起看呢？会不会20%的词（字）占了80%的出现次数？答案是肯定的。
 * 早在上个世纪30年代，就有人（Zipf）对此作出了研究，并给出了量化的表达——齐普夫定律（Zipf's Law）：
 * 一个词在一个有相当长度的语篇中的等级序号（该词在按出现次数排列的词表中的位置，他称之为rank，简称r）
 * 与该词的出现频率（他称为frequency，简称f）的乘积几乎是一个常数（constant，简称C）。用公式表示，就是 r × f = C 。
 * Zipf定律是文献计量学的重要定律之一，它和洛特卡定律、布拉德福定律一起被并称为文献计量学的三大定律。
 *
 * 产生服从zipf 分布的随机数
 */
public class ZipfGenerator {

    private Random rnd;
    //private Random rnd = new Random(0);
    private int size;
    private double skew;
    private double bottom;

    public ZipfGenerator(int size, double skew) {
        this.rnd = new Random(System.currentTimeMillis());
        this.skew = skew;
        this.setSize(size);
        

    }

    /**
     *
     * Method that returns a rank（等级） id between 0 and this.size (exclusive).
     * The frequency of returned rank id follows the Zipf distribution represented by this class.
     * @return a rank id between 0 and this.size.
     * @throws lptracegen.DistributionGenerator.DistributionException
     */

    public int next(){
        int rank = -1;
        double frequency = 0.0d;
        double dice = 0.0d;
        while (dice >= frequency) {
            rank = this.rnd.nextInt(this.size);
            frequency = getProbability(rank + 1);//(0 is not allowed for probability computation)
            dice = this.rnd.nextDouble();
        }
        return rank;
    }

    /**
     * Method that computes the probability（可能性） (0.0 .. 1.0) that a given rank occurs.
     * The rank cannot be zero.
     * @param rank
     * @return probability that the given rank occurs (over 1)
     * @throws lptracegen.DistributionGenerator.DistributionException
     */
    public double getProbability(int rank) {
        if (rank == 0) {
            throw new RuntimeException("getProbability - rank must be > 0");
        }
        return (1.0d / Math.pow(rank, this.skew)) / this.bottom;
    }
    
    public void setSize(int newSize){
      this.size = newSize;
      //calculate the generalized harmonic number of order 'size' of 'skew' 
      //http://en.wikipedia.org/wiki/Harmonic_number
      this.bottom = 0;
      for (int i = 1; i <= size; i++) {
          this.bottom += (1.0d / Math.pow(i, this.skew));
      }
    }

    /**
     * Method that returns a Zipf distribution
     * result[i] = probability that rank i occurs
     * @return the zipf distribution
     * @throws lptracegen.DistributionGenerator.DistributionException
     */
    public double[] getDistribution() {
        double[] result = new double[this.size];
        for ( int i = 1; i <= this.size; i++) { //i==0 is  not allowed to compute probability
            result[i - 1] = getProbability(i);
        }
        return result;
    }

    /**
     * Method that computes an array of length == this.size
     * with the occurrences for every rank i (following the Zipf)
     * result[i] = #occurrences of rank i
     * @param size
     * @return result[i] = #occurrences of rank i
     * @throws lptracegen.DistributionGenerator.DistributionException
     */
    public int[] getRankArray(int totalEvents) {
        int[] result = new int[this.size];
        for (int i = 0; i < totalEvents; i++) {
            int rank = next();
            result[rank] += 1;
        }
        return result;
    }
}

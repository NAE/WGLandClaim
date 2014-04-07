package com.gmail.mrphpfan;

public class FlagCost {
	private String flag;
	private double cost;
	
	public FlagCost(String flag, double cost){
		this.flag = flag.toLowerCase();
		this.cost = cost;
	}
	
	public String getFlag(){
		return flag;
	}
	
	public double getCost(){
		return cost;
	}
}

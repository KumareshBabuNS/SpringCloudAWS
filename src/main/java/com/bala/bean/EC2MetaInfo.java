package com.bala.bean;

import lombok.Data;


public @Data class EC2MetaInfo {


	private String amiId;
	private String hostname;
	private String instanceType;
	private String serviceDomain;


	public EC2MetaInfo(String amiId, String hostname, String instanceType, String serviceDomain) {
		
		this.amiId = amiId;
		this.hostname = hostname;
		this.instanceType = instanceType;
		this.serviceDomain = serviceDomain;
	}




}

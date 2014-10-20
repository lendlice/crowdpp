/*
	Copyright (c) 2009-2011
		Speech Group at Informatik 5, Univ. Erlangen-Nuremberg, GERMANY
		Korbinian Riedhammer
		Tobias Bocklet
		Florian Hoenig
		Stefan Steidl

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
package de.fau.cs.jstk.sampled;

import java.io.Serializable;

import javax.sound.sampled.Mixer;

/**
 * class intended for saving information (with XMLEncoder) about mixers used for playback or recording
 * 
 * @author hoenig
 *
 */
public class MixerDescription implements Serializable{	
	private static final long serialVersionUID = 1565291025106904209L;
	private String description = null;
	private String name = null;
	private String vendor = null;
	private String version = null;
	public MixerDescription(){};
	public MixerDescription(String description, String name, String vendor, String version){
		this.setDescription(description);
		this.setName(name);
		this.setVendor(vendor);
		this.setVersion(version);
	}
	
	public MixerDescription(Mixer.Info info){
		this(info.getDescription(),
			info.getName(),
			info.getVendor(),
			info.getVersion());
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getDescription() {
		return description;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getName() {
		return name;
	}
	public void setVendor(String vendor) {
		this.vendor = vendor;
	}
	public String getVendor() {
		return vendor;
	}
	public void setVersion(String version) {
		this.version = version;
	}
	public String getVersion() {
		return version;
	}
}
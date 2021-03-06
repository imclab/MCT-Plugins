/*******************************************************************************
 * Mission Control Technologies, Copyright (c) 2009-2012, United States Government
 * as represented by the Administrator of the National Aeronautics and Space 
 * Administration. All rights reserved.
 *
 * The MCT platform is licensed under the Apache License, Version 2.0 (the 
 * "License"); you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT 
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
 * License for the specific language governing permissions and limitations under 
 * the License.
 *
 * MCT includes source code licensed under additional open source licenses. See 
 * the MCT Open Source Licenses file included with this distribution or the About 
 * MCT Licenses dialog available at runtime from the MCT Help menu for additional 
 * information. 
 *******************************************************************************/
package gov.nasa.arc.mct.scenario.component;

import gov.nasa.arc.mct.components.JAXBModelStatePersistence;
import gov.nasa.arc.mct.components.ModelStatePersistence;
import gov.nasa.arc.mct.components.PropertyDescriptor;
import gov.nasa.arc.mct.components.PropertyDescriptor.VisualControlDescriptor;
import gov.nasa.arc.mct.components.PropertyEditor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An Activity Type exhibits reusable properties of an activity, 
 * such as power draw and comms usage.
 * 
 * @author vwoeltje
 *
 */
public class ActivityTypeComponent extends CostFunctionComponent {
	private AtomicReference<ActivityTypeModel> model = 
			new AtomicReference<ActivityTypeModel>(new ActivityTypeModel());
	private List<CostCapability> internalCosts = 
			Arrays.<CostCapability> asList(
					new ActivityTypeCost(false),
					new ActivityTypeCost(true));

	@Override
	public List<CostCapability> getInternalCosts() {
		return internalCosts;
	}

	@Override
	public boolean isLeaf() {
		return true;
	}
	
	public void setCosts(double power, double comms) {
		ActivityTypeModel m = model.get();
		m.setComms(comms);
		m.setPower(power);
	}
	
	@Override
	public <T> T handleGetCapability(Class<T> capability) {
		if (capability.isAssignableFrom(ModelStatePersistence.class)) {
			JAXBModelStatePersistence<ActivityTypeModel> persistence = 
					new JAXBModelStatePersistence<ActivityTypeModel>() {
				@Override
				protected ActivityTypeModel getStateToPersist() {
					return model.get();
				}

				@Override
				protected void setPersistentState(ActivityTypeModel modelState) {
					model.set(modelState);
				}

				@Override
				protected Class<ActivityTypeModel> getJAXBClass() {
					return ActivityTypeModel.class;
				}
			};

			return capability.cast(persistence);
		}
		return null;
	}
	
	@Override
	protected <T> List<T> handleGetCapabilities(Class<T> capability) {
		if (capability.isAssignableFrom(CostCapability.class)) {
			List<T> list = new ArrayList<T>();
			for (CostCapability cost : internalCosts) {
				list.add(capability.cast(cost));
			}
			return list;
		}
		return super.handleGetCapabilities(capability);
	}

	@Override
	public List<PropertyDescriptor> getFieldDescriptors()  {
		// Provide an ordered list of fields to be included in the MCT Platform's InfoView.
		List<PropertyDescriptor> fields = new ArrayList<PropertyDescriptor>();

		// Add custom UI for link to external resource
		PropertyDescriptor url = new PropertyDescriptor("External link", 
				new URLPropertyEditor(), 
				VisualControlDescriptor.Custom);
		url.setFieldMutable(true);

		fields.addAll(super.getFieldDescriptors()); // Costs
		fields.add(url);

		return fields;
	}
	
	private class ActivityTypeCost implements CostCapability {
		// Short-term approach; this should be changed to permit more variety
		// (i.e. not just Power/Comms)
		private boolean isComm;

		public ActivityTypeCost(boolean isComm) {
			this.isComm = isComm;
		}

		@Override
		public String getName() {
			return isComm ? "Comms" : "Power";
		}

		@Override
		public String getUnits() {
			return isComm ? "Kbps" : "Watts";
		}

		@Override
		public void setValue(double value) {
			ActivityTypeModel m = model.get();
			if (isComm) {
				m.setComms(value);
			} else {
				m.setPower(value);
			}
		}

		@Override
		public boolean isMutable() {
			return true;
		}

		@Override
		public double getValue() {
			ActivityTypeModel m = model.get();
			return (isComm) ? m.getComms() : m.getPower();
		}
	}
	
	private class URLPropertyEditor implements PropertyEditor<String> {

		@Override
		public String getAsText() {
			return model.get().getUrl();
		}

		@Override
		public void setAsText(String text) throws IllegalArgumentException {
			model.get().setUrl(text == null ? "" : text);
		}

		@Override
		public Object getValue() {
			return getAsText();
		}

		@Override
		public void setValue(Object value) throws IllegalArgumentException {
			setAsText(value == null ? "" : value.toString());
		}

		@Override
		public List<String> getTags() {
			return Collections.emptyList();
		}
		
	}
}

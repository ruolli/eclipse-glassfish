/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.enterprise.tools.verifier.tests.ejb;

import com.sun.enterprise.deployment.MethodDescriptor;
import com.sun.enterprise.tools.verifier.Result;
import com.sun.enterprise.tools.verifier.tests.ComponentNameConstructor;
import org.glassfish.ejb.deployment.descriptor.EjbDescriptor;
import org.glassfish.ejb.deployment.descriptor.EjbEntityDescriptor;
import org.glassfish.ejb.deployment.descriptor.EjbSessionDescriptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 
 * ContainerTransaction Style 3 - Each container transaction element consists 
 * of a list of one or more method elements, and the trans-attribute element. 
 * The container transaction element specifies that all the listed methods are 
 * assigned the specified transaction attribute value.
 *
 * Style 3: 
 *    <method> 
 *      <ejb-name> EJBNAME</ejb-name> 
 *      <method-name>METHOD</method-name> 
 *      <method-param> PARAMETER_1</method-param>
 *      ...
 *      <method-param> PARAMETER_N</method-param>
 *    </method> 
 * This style is used to refer to a single method within a set of methods with 
 * an overloaded name. The method must be one defined in the remote or home 
 * interface of the specified enterprise bean. If there is also a container
 * transaction element that uses the Style 2 element for the method name, or 
 * the Style 1 element for the bean, the value specified by the Style 3 element
 * takes precedence.
 */
public class ContainerTransactionStyle3 extends EjbTest implements EjbCheck { 
    private Result result = null;
    private ComponentNameConstructor compName = null;
    private EjbDescriptor descriptor = null;
    private Method[] homeMethods = null;
    private Method[] localHomeMethods = null;
    private Method[] remoteMethods = null;
    private Method[] localMethods = null;
    private Method[] serviceMethods = null;

      /**
     * ContainerTransaction Style 3 - Each container transaction element consists 
     * of a list of one or more method elements, and the trans-attribute element. 
     * The container transaction element specifies that all the listed methods are 
     * assigned the specified transaction attribute value.
     *
     * Style 3: 
     *    <method> 
     *      <ejb-name> EJBNAME</ejb-name> 
     *      <method-name>METHOD</method-name> 
     *      <method-param> PARAMETER_1</method-param>
     *      ...
     *      <method-param> PARAMETER_N</method-param>
     *    </method> 
     *
     * This style is used to refer to a single method within a set of methods with 
     * an overloaded name. The method must be one defined in the remote or home 
     * interface of the specified enterprise bean. If there is also a container
     * transaction element that uses the Style 2 element for the method name, or 
     * the Style 1 element for the bean, the value specified by the Style 3 element
     * takes precedence.
     *
     * @param descriptor the Enterprise Java Bean deployment descriptor
     *
     * @return <code>Result</code> the results for this assertion
     */
      public Result check(EjbDescriptor descriptor) {
          
          result = getInitializedResult();
          compName = getVerifierContext().getComponentNameConstructor();
          this.descriptor = descriptor;
          
          if (!(descriptor instanceof EjbSessionDescriptor) &&
                  !(descriptor instanceof EjbEntityDescriptor)) {
              result.notApplicable(smh.getLocalString
                                    ("com.sun.enterprise.tools.verifier.tests.ejb.homeintf.HomeMethodTest.notApplicable1",
                                    "Test apply only to session or entity beans."));
              return result;
          }
          
          // The method must be one defined in the remote, home or business interface of 
          // the specified enterprise bean.
          if (!descriptor.getMethodContainerTransactions().isEmpty()) 
              commonToAllInterfaces();
          
          if(result.getStatus() != Result.FAILED) {
              addGoodDetails(result, compName);
              result.passed(smh.getLocalString
                            (getClass().getName() + ".passed",
                            "Valid container transaction methods."));
          }
          return result;
      }

    /** 
     * Iterate over all the bean methods and check if they are present in any 
     * of the bean's interfaces.  
     */
    private void commonToAllInterfaces() {
        
        boolean isTimedObject=false;
        try {
            ClassLoader jcl = getVerifierContext().getClassLoader();
            Class beanClass = Class.forName(descriptor.getEjbClassName(), false, jcl);
            isTimedObject = javax.ejb.TimedObject.class.isAssignableFrom(beanClass);
        } catch (ClassNotFoundException e) {} //continue
        
        initializeMethods();
        for (Enumeration ee = descriptor.getMethodContainerTransactions().keys(); ee.hasMoreElements();) {
            MethodDescriptor methodDescriptor = (MethodDescriptor) ee.nextElement();
            
            if (methodDescriptor.getName().equals(MethodDescriptor.ALL_METHODS) ||
                    methodDescriptor.getParameterClassNames() == null) // style 1 and 2
                continue;
            
            if(isTimedObject && 
                    MethodDescriptor.EJB_BEAN.equals(methodDescriptor.getEjbClassSymbol()) &&
                    methodDescriptor.getName().equals("ejbTimeout")) {
                String[] params=methodDescriptor.getJavaParameterClassNames();
                if(params.length==1 && params[0].trim().equals("javax.ejb.Timer"))
                    continue;//found a match
            }//if implements timer
            
            Set<Method> methods = getAllInterfaceMethods(methodDescriptor);
            
            if(!isMethodContained(methods, methodDescriptor)) { // report failure
                String ejbClassSymbol = methodDescriptor.getEjbClassSymbol();
                String intf = ejbClassSymbol;
                if(ejbClassSymbol == null) {
                    intf = smh.getLocalString(getClass().getName() + ".msg", "any of bean");
                } else if(ejbClassSymbol.equals(MethodDescriptor.EJB_REMOTE)) {
                    intf = "Remote or RemoteBusiness";
                } else if(ejbClassSymbol.equals(MethodDescriptor.EJB_LOCAL)) { 
                    intf = "Local or LocalBusiness";
                }

                addErrorDetails(result, compName);
                result.failed(smh.getLocalString
                        (getClass().getName() + ".failed",
                        "Error: Container Transaction method name [ {0} ] not " +
                        "defined in [ {1} ] interface(s).",
                        new Object[] {methodDescriptor.getName(), intf}));
            }
        }
    }
    
    /** get methods from all of bean's interfaces*/
    private void initializeMethods() {
        homeMethods = getMethods(descriptor.getHomeClassName());
        localHomeMethods = getMethods(descriptor.getLocalHomeClassName());
        remoteMethods = getMethods(descriptor.getRemoteClassName());
        remoteMethods = getBusinessMethods(descriptor.getRemoteBusinessClassNames(),remoteMethods);
        localMethods = getMethods(descriptor.getLocalClassName());
        localMethods = getBusinessMethods(descriptor.getLocalBusinessClassNames(), localMethods);
        serviceMethods = getMethods(descriptor.getWebServiceEndpointInterfaceName());
    }
    
    /** Collect all the methods the bean interfaces specified in methodDescriptor*/
    private Set<Method> getAllInterfaceMethods(MethodDescriptor methodDescriptor) {
        
        Set<Method> methods = new HashSet<Method>();

        String methodIntf = methodDescriptor.getEjbClassSymbol();
        if((methodIntf == null)) {
            methods.addAll(Arrays.asList(homeMethods));
            methods.addAll(Arrays.asList(localHomeMethods));
            methods.addAll(Arrays.asList(remoteMethods));
            methods.addAll(Arrays.asList(localMethods));
            methods.addAll(Arrays.asList(serviceMethods));
        } else if(methodIntf.equals(MethodDescriptor.EJB_HOME)) { 
            methods.addAll(Arrays.asList(homeMethods));
        } else if(methodIntf.equals(MethodDescriptor.EJB_LOCALHOME)) { 
            methods.addAll(Arrays.asList(localHomeMethods));
        } else if(methodIntf.equals(MethodDescriptor.EJB_REMOTE)) {
            methods.addAll(Arrays.asList(remoteMethods));
        } else if(methodIntf.equals(MethodDescriptor.EJB_LOCAL)) { 
            methods.addAll(Arrays.asList(localMethods));
        } else if(methodIntf.equals(MethodDescriptor.EJB_WEB_SERVICE)) { 
            methods.addAll(Arrays.asList(serviceMethods));
        }
        return methods;
    }
    
    /** get all the methods defined in the given class clsName*/
    private Method[] getMethods(String clsName) {
        if(clsName==null)
            return new Method[]{};
        ClassLoader jcl = getVerifierContext().getClassLoader();
        try {
            Class cls = Class.forName(clsName, false, jcl);
            return cls.getMethods();
        } catch (ClassNotFoundException e) {} // ignore and continue
        return new Method[]{};
    }
    /** returns an array of business methods that are collected from set classNames*/
    private Method[] getBusinessMethods(Set<String> classNames, Method[] intfMethods) {
        if(!classNames.isEmpty()) {
            
            List<Method> methods = new ArrayList<Method>();
            for (String clsName : classNames) {
                Method[] methodArray = getMethods(clsName);
                if(methodArray != null)
                    methods.addAll(Arrays.asList(methodArray));
            }
            
            if(intfMethods != null)
                methods.addAll(Arrays.asList(intfMethods));
            return methods.toArray(new Method[]{});
        }
        return intfMethods;
    }
    
    /** returns true if methodDescriptor is contained in the set methods */
    private boolean isMethodContained(Set<Method> methods, MethodDescriptor methodDescriptor) {
        boolean foundIt = false;
        for (Method method : methods) {
            if(method.getName().equals(methodDescriptor.getName()) &&  
                    MethodUtils.stringArrayEquals(methodDescriptor.getParameterClassNames(), 
                            (new MethodDescriptor(method)).getParameterClassNames())) {
                foundIt = true;
                break;
            }
        }
        return foundIt;
    }
}

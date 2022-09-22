<#-- To render the third-party file.
 Available context :
 - dependencyMap a collection of Map.Entry with
   key are dependencies (as a MavenProject) (from the maven project)
   values are licenses of each dependency (array of string)
 - licenseMap a collection of Map.Entry with
   key are licenses of each dependency (array of string)
   values are all dependencies using this license
-->
<#function getUrl license>
  <#if license == "Apache-2.0">
    <#return "https://opensource.org/licenses/Apache-2.0">
  <#elseif license == "MIT">
    <#return "https://opensource.org/licenses/MIT">
  <#elseif license == "BSD-3-Clause">
    <#return "https://opensource.org/licenses/BSD-3-Clause">
  <#elseif license == "EPL-2.0">
    <#return "https://www.eclipse.org/legal/epl-2.0/">
  <#elseif license == "GPLv2 with Classpath Exception">
    <#return "https://www.gnu.org/software/classpath/license.html">
  <#elseif license == "CDDLv1.0">
    <#return "https://opensource.org/licenses/CDDL-1.0">
  <#elseif license == "CDDLv1.1">
    <#return "https://spdx.org/licenses/CDDL-1.1.html">
  <#elseif license == "Bouncy Castle">
    <#return "https://www.bouncycastle.org/licence.html">
  <#else>
    <#return "no known URL">
  </#if>
</#function>
<#function formatLicenses licenses>
  <#assign licenseText = ""/>
  <#list licenses as license>
    <#local licenseText=licenseText + ", " + license + " (URL: " + getUrl(license) + ")">
  </#list>
  <#return licenseText?substring(2)>
</#function>
<#-- START Third-party license text -->
Camunda Amazon SQS Connector

THIRD-PARTY SOFTWARE NOTICES AND INFORMATION
Do Not Translate or Localize

This project incorporates components with the licenses under which Camunda received them as listed below.

<#list dependencyMap as e>
  <#assign p=e.getKey() />
  <#assign licenses=e.getValue() />
  * ${p.groupId + ":" + p.artifactId + " (Version: " + p.version + ", License: " + formatLicenses(licenses) + ")"}
</#list>
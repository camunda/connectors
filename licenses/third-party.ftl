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
    <#elseif license == "BSD-2-Clause">
        <#return "https://opensource.org/licenses/BSD-2-Clause">
    <#elseif license == "BSD-3-Clause">
        <#return "https://opensource.org/licenses/BSD-3-Clause">
    <#elseif license == "EPL-2.0">
        <#return "https://www.eclipse.org/legal/epl-2.0/">
    <#elseif license == "EPL-1.0">
        <#return "https://www.eclipse.org/legal/epl-v10.html">
    <#elseif license == "GPLv2">
        <#return "https://www.gnu.org/licenses/old-licenses/gpl-2.0.html">
    <#elseif license == "GPLv2 with Classpath Exception">
        <#return "https://www.gnu.org/software/classpath/license.html">
    <#elseif license == "GNU Lesser General Public License, Version 2.1">
        <#return "http://www.gnu.org/licenses/lgpl-2.1.html">
    <#elseif license == "CDDLv1.0">
        <#return "https://opensource.org/licenses/CDDL-1.0">
    <#elseif license == "CDDLv1.1">
        <#return "https://spdx.org/licenses/CDDL-1.1.html">
    <#elseif license == "Bouncy Castle">
        <#return "https://www.bouncycastle.org/licence.html">
    <#elseif license == "Go License">
        <#return "https://go.dev/LICENSE">
    <#elseif license == "MPL 2.0">
        <#return "https://www.mozilla.org/en-US/MPL/2.0/">
    <#elseif license == "Public Domain, per Creative Commons CC0">
        <#return "https://creativecommons.org/publicdomain/zero/1.0/deed.en">
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
THIRD-PARTY SOFTWARE NOTICES AND INFORMATION
Do Not Translate or Localize

This project incorporates components with the licenses under which Camunda received them as listed below.

<#list dependencyMap as e>
    <#assign p=e.getKey() />
    <#assign licenses=e.getValue() />
  * ${p.groupId + ":" + p.artifactId + " (Version: " + p.version + ", License: " + formatLicenses(licenses) + ")"}
</#list>
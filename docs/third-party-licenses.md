# Managing third party dependencies

All Connector-related projects consume various third party dependencies/libraries.
All those dependencies come with licenses.

All components that we distribute to users (Connector SDK, Connector runtime, out-of-the-box Connectors)
need to declare which third party dependencies under which licenses they consume and re-distribute (if they do, like the pre-packaged Connector runtime).

Please be aware that there is a company [Stop & Go list](https://confluence.camunda.com/pages/viewpage.action?pageId=98605302)
(:lock: Confluence access needed) regarding the licenses we can consume and re-distribute.

Please check the libraries you want to add to a project regarding their licenses and if we can consume them.
If in doubt, contact the team's Engineering Manager.

## Third party notices

All relevant projects (see above) produce a `THIRD_PARTY_NOTICES` file as part of their build.
This repository has CI license checks (see `.github/workflows/CHECK_LICENSES.yml`) to detect newly introduced license issues on pull requests and releases.

Checking those Pull Requests thoroughly is a vital part of the development cycle.
The following scenarios can occur:

1. The version of a library was updated, the license has not changed.
   - You can approve and merge this change.
2. A new library was added or the license has changed with a new version, the license was discovered and successfully matched.
   - Check that the license can be consumed. If in doubt, contact the team's Engineering Manager.
   - If the license can be consumed, you can approve and merge this change. If not, see that the library in question is removed or downgraded if possible.
   - After updating the dependencies, the PR will be updated accordingly by the GitHub workflow.
3. A new library was added or the license has changed with a new version, the license was discovered but not matched.
   - You can find `no known URL` in the `THIRD_PARTY_NOTICES` file.
   - Add the license name as a new entry (or to any of the known licenses) in the `licenseMerges` section of the [parent POM](https://github.com/camunda/connectors/blob/main/parent/pom.xml). Entries are separated by a pipe `|`; the first entry of each item defines the license that is used in the end. License merges are described in the [plugin documentation](https://www.mojohaus.org/license-maven-plugin/examples/example-thirdparty.html#Merge_licenses.).
   - If the license is completely new, add an entry for the license's URL in the license template (`third-party.ftl`) inherited from the `connector-parent`. The license key is the first entry in the `licenseMerge` as mentioned before.
   - Adjusting and pushing your project's license template file will retrigger the GitHub workflow and update the PR.
4. A new library was added or the license has changed with a new version, the license was not discovered.
   - You can find `Unknown license` in the `THIRD_PARTY_NOTICES` file.
   - Add the license information to the `THIRD-PARTY.properties` file. The format is `<groupId>--<artifactId>--<version>=<license>`. This applies to all Connectors projects that inherit from the `connector-parent`.
   - Retrigger the GitHub workflow in your repository once the missing license has been added. The PR will be updated accordingly.

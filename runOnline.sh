#!/bin/bash

readonly BASE_DIR=$(realpath "$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )")

readonly SIMPLE_AQUAINTENCES_DIR="${BASE_DIR}/../psl-examples/simple-acquaintances"

readonly JAVA_MEM_GB=16

readonly PSL_VERSION="2.2.2-SNAPSHOT"
readonly STRING_IDS='simple-acquaintances'
readonly BASE_PSL_OPTION="--postgres ${POSTGRES_DB} -D log4j.threshold=TRACE -D persistedatommanager.throwaccessexception=false"
readonly onlineOptions="--infer SGDOnlineInference -D sgdOnline.tolerance=0.000001 -D sgdOnline.maxiterations=500 -D sgdOnline.truncateeverystep=false -D onlinetermstore.randomizepageaccess=false -D onlinetermstore.shufflepage=false"

readonly BUILD_PATH="/Users/charlesdickens/.m2/repository/org/linqs/psl-cli"

function main() {
   trap exit SIGINT

   build

   # TODO: this is hacky, should be on develop branch
   mv_builds

   standard_fixes

   run
}

function mv_builds() {
  rm -r "${BUILD_PATH}/2.2.2-SNAPSHOT"
  mv "${BUILD_PATH}/2.2.2" "${BUILD_PATH}/2.2.2-SNAPSHOT"
  mv "${BUILD_PATH}/2.2.2-SNAPSHOT/psl-cli-2.2.2.jar" "${BUILD_PATH}/2.2.2-SNAPSHOT/psl-cli-2.2.2-SNAPSHOT.jar"
}

function build() {
  mvn clean install -D skipTests
}

function run() {
   pushd . > /dev/null

   cd "${SIMPLE_AQUAINTENCES_DIR}/cli"

   ./run.sh ${onlineOptions}

   popd > /dev/null

   cat ../psl-examples/simple-acquaintances/cli/inferred-predicates/KNOWS.txt | grep "0\t1"
}

# Common to all examples.
function standard_fixes() {
    local exampleDir=${SIMPLE_AQUAINTENCES_DIR}
    local baseName=`basename ${exampleDir}`
    local options=''

    # Check for int ids.
    if [[ "${STRING_IDS}" != *"${baseName}"* ]]; then
        options="--int-ids ${options}"
    fi

    pushd . > /dev/null
        cd "${exampleDir}/cli"

        # Always create a -leared version of the model in case this example has weight learning.
        cp "${baseName}.psl" "${baseName}-learned.psl"

        # Increase memory allocation.
        sed -i "s/java -jar/java -Xmx${JAVA_MEM_GB}G -Xms${JAVA_MEM_GB}G -jar/" run.sh

        # Set the PSL version.
        sed -i "s/^readonly PSL_VERSION='.*'$/readonly PSL_VERSION='${PSL_VERSION}'/" run.sh

        # Disable weight learning.
        sed -i 's/^\(\s\+\)runWeightLearning/\1# runWeightLearning/' run.sh

        # Add in the additional options.
        sed -i "s/^readonly ADDITIONAL_PSL_OPTIONS='.*'$/readonly ADDITIONAL_PSL_OPTIONS='${BASE_PSL_OPTION} ${options}'/" run.sh

        # Disable evaluation, we are only looking for objective values.
        sed -i "s/^readonly ADDITIONAL_EVAL_OPTIONS='.*'$/readonly ADDITIONAL_EVAL_OPTIONS='--infer'/" run.sh
    popd > /dev/null
}

main "$@"
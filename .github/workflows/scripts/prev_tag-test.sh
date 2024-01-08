#!/bin/bash
# tests only for local running, to verify that prev_tag.sh works as expected
# Function to create a mock git repository
setup_mock_repo() {
  mkdir temp_repo
  cd temp_repo
  git init
  # Create a dummy commit to enable tagging
  touch dummy.txt
  git add dummy.txt
  git commit -m "Initial commit"

  # Add mock tags to simulate different scenarios
  git tag 0.5.1
  git tag 0.7.3
  git tag 0.11.0
  git tag 0.23.2
  git tag 7.4.0
  git tag 8.2.0
  git tag 8.2.1
  git tag 8.3.0-alpha1
  git tag 8.3.0-rc1
  git tag 8.3.0
  git tag 8.3.1-rc1
  git tag 8.3.1
  git tag 8.3.2-rc1
  git tag 8.3.2-rc2
  git tag 8.3.2-rc5
  git tag 8.3.2-rc11
  git tag 8.3.2
  git tag 8.3.3
  git tag 8.4.0-alpha1
  git tag 8.4.0-alpha2
  git tag 8.4.0-alpha2-rc1
  git tag 8.4.0-alpha2-rc2
  git tag 8.4.0-alpha2-rc3
  git tag 8.4.0-rc1
  git tag 8.4.0-rc2
  git tag 8.4.0-rc3
  git tag 8.4.0-rc23
  git tag 8.4.0
  git tag 8.4.1-alpha1
  git tag 8.4.2
  git tag 8.4.2-alpha1
  git tag 9.0.0-alpha1
  git tag 9.0.0-alpha2-rc1
  git tag 9.0.0-alpha2
  git tag 9.0.0-alpha3-rc1
  git tag 9.0.0-rc1
  git tag 9.0.0
  git tag 9.1.0
  git tag 9.2.0-alpha2
  git tag 9.2.0-rc2
  git tag 11.2.0
  git tag 12.0.0
  git tag 12.2.0
  git tag 15.0.0-rc1
  git tag 15.0.0
  git tag 16.0.0-rc2
  git tag 16.0.0-alpha2
  git tag 19.0.0-alpha3
  git tag 19.0.3-alpha3
  git tag 19.0.3-rc3
  git tag 19.0.3
  git tag 19.0.4-alpha2
  git tag 19.0.4-alpha6
  git tag 20.3.0
  git tag 20.3.1-alpha2
  git tag 20.3.1-alpha5
  git tag 20.3.1-alpha11
  git tag 20.3.1-alpha23
  git tag 20.4.1-alpha1-rc11
  git tag 20.4.1-alpha1-rc3
  git tag 20.4.1-alpha1-rc20
  git tag 20.4.1-alpha11
  git tag 20.4.1-alpha30-rc1
  git tag 20.4.1-alpha2-rc1
}

# Function to clean up the mock repository
cleanup() {
  cd ..
  rm -rf temp_repo
}

# Initialize a variable to store test results
passed_tests_results=""
fail_test_results=""
test_passed_count=0
test_fail_count=0

# Function to run a single test case
run_test() {
  input_tag=$1
  expected_output=$2

  output=$(bash ../prev_tag.sh $input_tag)
  if [[ $output == *"$expected_output"* ]]; then
    passed_tests_results+="Test passed for input $input_tag: Expected and got $expected_output\n"
    ((test_passed_count++))
  else
    fail_test_results+="Test failed for input $input_tag: Expected $expected_output, got $output\n"
    ((test_fail_count++))
  fi
}

# Main test logic
setup_mock_repo
# input : arg 1 -> input tag, arg 2-> expected output
run_test "8.4.0" "8.3.0" # Normal release, next lower tag in the same minor series
run_test "8.3.0" "8.2.0"
run_test "8.3.3" "8.3.2" # Normal release, next lower tag
run_test "8.3.1" "8.3.0" # Normal release, next lower tag

run_test "8.4.0-alpha2" "8.4.0-alpha1" # Alpha version, next lower alpha tag
run_test "8.3.0-alpha1" "8.2.0" # First alpha of a new minor version

run_test "8.4.0-alpha2-rc3" "8.4.0-alpha2-rc2" # Alpha RC, next lower alpha RC tag
run_test "8.4.0-alpha2-rc2" "8.4.0-alpha2-rc1" # Alpha RC, next lower alpha RC tag
run_test "8.4.0-alpha2-rc1" "8.4.0-alpha1" # First alpha RC of a new alpha version
run_test "9.0.0-alpha2-rc1" "9.0.0-alpha1" # First alpha RC of a new alpha version
run_test "9.0.0-alpha3-rc1" "9.0.0-alpha2" # First alpha RC of a new alpha version
run_test "8.4.0-alpha1" "8.3.0" # First alpha of a new alpha version

run_test "8.4.1-alpha1" "8.4.0" # First alpha of a new alpha version
run_test "8.4.2-alpha1" "8.4.1" # First alpha of a new alpha version

run_test "8.3.2-rc1" "8.3.1" # RC for normal release, next lower normal tag
run_test "8.3.2-rc2" "8.3.2-rc1" # Next RC in series
run_test "8.3.2-rc5" "8.3.2-rc2" # Next RC in series

run_test "8.4.0-rc1" "8.3.0" # RC for normal release, next lower normal tag
run_test "8.4.0-rc2" "8.4.0-rc1" # Next RC in series
run_test "8.4.0-rc3" "8.4.0-rc2" # Next RC in series

run_test "8.4.0-alpha2" "8.4.0-alpha1" # Next alpha in series

run_test "9.0.0" "8.4.0" # First release in a new major version
run_test "9.1.0" "9.0.0" # First release in a new minor version after a major version

#additional test cases
run_test "9.2.0-alpha2" "9.1.0" # Missed alpha1
run_test "9.2.0-rc2" "9.1.0" # Missed rc1
run_test "8.2.0" "7.4.0"
run_test "7.4.0" "0.23.2"
run_test "0.23.2" "0.11.0"
run_test "0.11.0" "0.7.3"
run_test "0.7.3" "0.5.1"
run_test "12.0.0" "11.2.0"
run_test "15.0.0" "12.2.0"
run_test "15.0.0-rc1" "12.2.0"
run_test "15.0.3-rc2" "5.0.0"
run_test "16.0.0-rc2" "15.0.0"
run_test "19.0.0-alpha3" "15.0.0"
run_test "19.0.3-alpha3" "15.0.0"
run_test "19.0.3-rc3" "15.0.0"
run_test "19.0.4-alpha11" "19.0.4-alpha6"
run_test "20.3.1-alpha5" "20.3.1-alpha2"
run_test "20.3.1-alpha23" "20.3.1-alpha11"
run_test "20.4.1-alpha1-rc20" "20.4.1-alpha1-rc11"
run_test "20.4.1-alpha1-rc11" "20.4.1-alpha1-rc3"
run_test "20.4.1-alpha1-rc3" "20.3.0"
run_test "20.4.1-alpha30-rc1" "20.4.1-alpha11"
run_test "20.4.1-alpha2-rc1" "20.3.0"

# Invalid Tag Format
run_test "invalid-tag" "Release tag is invalid" # Invalid tag format
run_test "8.4" "Release tag is invalid" # Incomplete tag

# Clean up after tests
cleanup

# Output all test results at the end
echo -e "$passed_tests_results"
echo -e "$fail_test_results"
echo "Total tests passed: $test_passed_count"
echo "Total tests fail: $test_fail_count"

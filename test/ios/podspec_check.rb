# The podspec compiles the library's own iOS sources and nothing else: a test file picked up by
# the pod (test/ios/RouteTests.m defines main) breaks the app's link with a duplicate _main.
#   ruby test/ios/podspec_check.rb      (plain Ruby, no CocoaPods needed; run.sh runs it first)
# Evaluates the podspec against a stand-in for Pod::Spec that records the attributes, and globs
# source_files minus exclude_files from the package root, as CocoaPods' FileAccessor does.
module Pod
  class Spec
    attr_reader :attrs, :ios
    def initialize
      @attrs = {}
      @ios = Recorder.new(@attrs, 'ios.')
      yield self
    end
    def method_missing(name, *args)
      key = name.to_s
      key.end_with?('=') ? @attrs[key.chomp('=')] = args.first : @attrs[key]
    end
    def respond_to_missing?(*) = true
    def dependency(*) end
  end
  class Recorder
    def initialize(attrs, prefix) = (@attrs, @prefix = attrs, prefix)
    def method_missing(name, *args) = @attrs[@prefix + name.to_s.chomp('=')] = args.first
    def respond_to_missing?(*) = true
  end
end

root = File.expand_path('../..', __dir__)
spec = Dir.chdir(root) { eval(File.read('ReactNativeIncallManager.podspec'), binding, 'ReactNativeIncallManager.podspec') }
glob = ->(patterns) { Array(patterns).flat_map { |p| Dir.glob(p, File::FNM_EXTGLOB, base: root) } }
files = (glob.(spec.attrs['source_files']) - glob.(spec.attrs['exclude_files'])).select { |f| File.file?(File.join(root, f)) }.sort
expected = ['ios/RNInCallManager/RNInCallManager.h', 'ios/RNInCallManager/RNInCallManager.m']
if files == expected
  puts "ok   podspec source_files: #{files.join(', ')}"
else
  puts "FAIL podspec source_files: #{files.join(', ')} (expected #{expected.join(', ')})"
  exit 1
end

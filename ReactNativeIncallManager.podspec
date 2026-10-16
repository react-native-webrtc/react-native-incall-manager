require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|
  s.name                   = 'ReactNativeIncallManager'
  s.version                = package['version']
  s.summary                = package['description']
  s.description            = package['description']
  s.homepage               = package['homepage']
  s.license                = package['license']
  s.author                 = package['author']
  s.source                 = { :git => 'https://github.com/zxcpoiu/react-native-incall-manager.git', :tag => s.version }

  # React Native's own minimum when the Podfile has loaded react_native_pods.rb
  # (15.1 for RN 0.76+); 12.4, RN 0.71's minimum, otherwise.
  s.platforms              = { :ios => defined?(min_ios_version_supported) ? min_ios_version_supported : '12.4' }

  s.preserve_paths         = 'LICENSE', 'package.json'
  s.source_files           = '**/*.{h,m}'
  s.exclude_files          = 'example/**/*'
  s.dependency               'React-Core'
end

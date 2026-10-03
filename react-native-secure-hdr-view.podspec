require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "react-native-secure-hdr-view"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = "https://github.com/sonodima/react-native-secure-hdr-view"
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => min_ios_version_supported }
  s.source       = { :git => "https://github.com/sonodima/react-native-secure-hdr-view.git", :tag => "v#{s.version}" }

  s.source_files = "ios/**/*.{h,mm}"

  install_modules_dependencies(s)
end

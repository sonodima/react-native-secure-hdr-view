import type { ViewProps } from 'react-native';
import codegenNativeComponent from 'react-native/Libraries/Utilities/codegenNativeComponent';
import type { Float, WithDefault } from 'react-native/Libraries/Types/CodegenTypes';

export interface NativeProps extends ViewProps {
  secure?: WithDefault<boolean, false>;
  hdr?: WithDefault<Float, 0>;
}

export default codegenNativeComponent<NativeProps>('RNSecureHDRView');

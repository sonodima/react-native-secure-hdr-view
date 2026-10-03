import { StyleSheet, type StyleProp, type ViewStyle } from 'react-native';

import NativeSecureHDRView, {
  type NativeProps,
} from './SecureHDRViewNativeComponent';

type FrameStyle = Omit<
  ViewStyle,
  Extract<
    keyof ViewStyle,
    | `background${string}`
    | `experimental_background${string}`
    | `border${string}Color`
    | `border${string}Width`
    | 'borderStyle'
    | `outline${string}`
  >
>;

export type SecureHDRViewProps = Omit<NativeProps, 'hdr' | 'style'> & {
  hdr?: boolean | number;
  style?: StyleProp<FrameStyle>;
};

export const SecureHDRView = ({ hdr, style, ...props }: SecureHDRViewProps) => (
  <NativeSecureHDRView
    {...props}
    hdr={intensity(hdr)}
    style={[style, styles.clip]}
  />
);

const intensity = (hdr: SecureHDRViewProps['hdr']) => {
  if (typeof hdr === 'number') {
    return hdr > 0 ? Math.min(hdr, 1) : 0;
  }
  return hdr ? 1 : 0;
};

const styles = StyleSheet.create({
  clip: {
    overflow: 'hidden',
  },
});

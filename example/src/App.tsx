import { useState } from 'react';
import { ScrollView, StyleSheet, Switch, Text, View } from 'react-native';
import Slider from '@react-native-community/slider';
import { SecureHDRView } from 'react-native-secure-hdr-view';

export default function App() {
  const [secure, setSecure] = useState(true);
  const [hdr, setHdr] = useState(1);

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <Text style={styles.title}>Secure HDR View</Text>

      <SecureHDRView secure={secure} hdr={hdr} style={styles.passFrame}>
        <View style={styles.pass}>
          <Text style={styles.label}>ACCESS CODE</Text>
          <Text style={styles.code}>4821 9937</Text>
        </View>
      </SecureHDRView>

      <View style={styles.row}>
        <SecureHDRView secure={secure} hdr={hdr} style={styles.badgeFrame}>
          <View style={[styles.badge, styles.vip]}>
            <Text style={styles.badgeText}>VIP</Text>
          </View>
        </SecureHDRView>
        <SecureHDRView secure={secure} hdr={hdr} style={styles.badgeFrame}>
          <View style={[styles.badge, styles.gate]}>
            <Text style={styles.badgeText}>Gate B12</Text>
          </View>
        </SecureHDRView>
      </View>

      <View style={styles.panel}>
        <View style={styles.control}>
          <Text style={styles.controlLabel}>Secure</Text>
          <Switch value={secure} onValueChange={setSecure} />
        </View>
        <View style={styles.control}>
          <Text style={styles.controlLabel}>HDR</Text>
          <Text style={styles.controlValue}>{Math.round(hdr * 100)}%</Text>
        </View>
        <Slider value={hdr} onValueChange={setHdr} />
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: {
    backgroundColor: '#f2f2f7',
  },
  content: {
    gap: 20,
    padding: 24,
    paddingTop: 80,
  },
  title: {
    fontSize: 28,
    fontWeight: '800',
  },
  passFrame: {
    borderRadius: 24,
  },
  pass: {
    alignItems: 'center',
    gap: 6,
    padding: 32,
    backgroundColor: 'white',
  },
  label: {
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 2,
    color: '#8e8e93',
  },
  code: {
    fontSize: 40,
    fontWeight: '800',
  },
  row: {
    flexDirection: 'row',
    gap: 12,
  },
  badgeFrame: {
    flex: 1,
    borderRadius: 16,
  },
  badge: {
    alignItems: 'center',
    padding: 20,
  },
  vip: {
    backgroundColor: '#111',
  },
  gate: {
    backgroundColor: '#ff6a00',
  },
  badgeText: {
    fontSize: 18,
    fontWeight: '700',
    color: 'white',
  },
  panel: {
    gap: 12,
    padding: 16,
    borderRadius: 20,
    backgroundColor: 'white',
  },
  control: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  controlLabel: {
    fontSize: 17,
    fontWeight: '600',
  },
  controlValue: {
    fontSize: 17,
    fontVariant: ['tabular-nums'],
    color: '#8e8e93',
  },
});

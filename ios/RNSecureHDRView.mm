#import "RNSecureHDRView.h"

#import <react/renderer/components/SecureHDRViewSpec/ComponentDescriptors.h>
#import <react/renderer/components/SecureHDRViewSpec/Props.h>

using namespace facebook::react;

@implementation RNSecureHDRView {
  UIView *_secureContainer;
  UIView *_plainContainer;
  UIView *_content;
  CALayer *_hdr;
  CGFloat _headroom;
}

+ (ComponentDescriptorProvider)componentDescriptorProvider
{
  return concreteComponentDescriptorProvider<RNSecureHDRViewComponentDescriptor>();
}

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
    static const auto defaultProps = std::make_shared<const RNSecureHDRViewProps>();
    _props = defaultProps;

    // The canvas of a secure text field is left out of screenshots and recordings.
    UITextField *field = [UITextField new];
    field.secureTextEntry = YES;
    _secureContainer = field.subviews.firstObject ?: [UIView new];
    [_secureContainer.subviews makeObjectsPerformSelector:@selector(removeFromSuperview)];
    _secureContainer.userInteractionEnabled = YES;
    _plainContainer = [UIView new];
    [self addSubview:_plainContainer];
    [self addSubview:_secureContainer];

    _content = [UIView new];
    _hdr = [CALayer layer];
    _hdr.hidden = YES;
    _hdr.compositingFilter = @"multiplyBlendMode";
    [self setUpHDR];
    [self setSecure:NO];
  }
  return self;
}

// The brightest white the display can show, as a multiple of SDR white.
- (void)setUpHDR
{
  _headroom = 1;
  if (@available(iOS 17.0, *)) {
    _headroom = UIScreen.mainScreen.potentialEDRHeadroom;
    if (@available(iOS 26.0, *)) {
      _hdr.preferredDynamicRange = CADynamicRangeHigh;
    } else {
      _hdr.wantsExtendedDynamicRangeContent = YES;
    }
  }
}

- (void)mountChildComponentView:(UIView<RCTComponentViewProtocol> *)childComponentView index:(NSInteger)index
{
  [_content insertSubview:childComponentView atIndex:index];
}

- (void)unmountChildComponentView:(UIView<RCTComponentViewProtocol> *)childComponentView index:(NSInteger)index
{
  [childComponentView removeFromSuperview];
}

- (void)updateProps:(Props::Shared const &)props oldProps:(Props::Shared const &)oldProps
{
  const auto &newProps = *std::static_pointer_cast<RNSecureHDRViewProps const>(props);
  [self setSecure:newProps.secure];
  [self setHDR:newProps.hdr];
  [super updateProps:props oldProps:oldProps];
}

- (void)layoutSubviews
{
  [super layoutSubviews];
  [CATransaction begin];
  [CATransaction setDisableActions:YES];
  _plainContainer.frame = _secureContainer.frame = self.bounds;
  _content.frame = _hdr.frame = _plainContainer.bounds;
  [CATransaction commit];
}

- (void)setSecure:(BOOL)secure
{
  UIView *container = secure ? _secureContainer : _plainContainer;
  if (_content.superview == container) {
    return;
  }

  [container addSubview:_content];
  [container.layer addSublayer:_hdr];
}

// If we multiply the content by an HDR white, SDR white gets brighter, up to the headroom.
// The intensity is spread evenly in stops, close to how brightness is perceived.
- (void)setHDR:(CGFloat)intensity
{
  CGFloat stops = intensity * log2(_headroom);
  [CATransaction begin];
  [CATransaction setDisableActions:YES];
  _hdr.hidden = intensity <= 0;
  if (@available(iOS 26.0, *)) {
    UIColor *white = [UIColor colorWithRed:1 green:1 blue:1 alpha:1 exposure:stops];
    _hdr.backgroundColor = [white colorByApplyingContentHeadroom:_headroom].CGColor;
  } else {
    CGFloat gain = exp2(stops);
    CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceExtendedLinearSRGB);
    const CGFloat white[] = {gain, gain, gain, 1};
    CGColorRef color = CGColorCreate(space, white);
    _hdr.backgroundColor = color;
    CGColorRelease(color);
    CGColorSpaceRelease(space);
  }
  [CATransaction commit];
}

@end

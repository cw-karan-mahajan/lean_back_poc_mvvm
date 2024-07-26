//public class CardPresenter extends Presenter {
//
//    public CardPresenter() {}
//
//    @Override
//    public ViewHolder onCreateViewHolder(ViewGroup parent) {
//        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_layout, parent, false);
//        try {
//            Drawable drawable = ((CloudwalkerApplication) parent.getContext().getApplicationContext()).getResourcesUtils().getDrawable("focus_on_select_bg");
//            v.setBackground(drawable);
//        } catch (Exception e) {
//            Timber.e(e);
//        }
//        return new ViewHolder(v);
//    }
//
//    @Override
//    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
//        final ImageView posterImageView;
//        posterImageView = viewHolder.view.findViewById(R.id.posterImageView);
//
//        if (item instanceof MovieTile) {
//            final MovieTile movieTile = (MovieTile) item;
//            if (movieTile.getTileWidth() != null && movieTile.getTileHeight() != null && movieTile.getPoster() != null && movieTile.getRowLayout() != null) {
//
//
//                if (movieTile.getRowLayout().compareToIgnoreCase("landscape") == 0) {
//                    GlideApp.with(viewHolder.view.getContext())
//                            .load(makeSecure(movieTile.getPoster()))
//                            .override(dpToPx(viewHolder.view.getContext(), Integer.parseInt(movieTile.getTileWidth())), dpToPx(viewHolder.view.getContext(), Integer.parseInt(movieTile.getTileHeight())))
//                            .into(posterImageView);
//
//                } else if (movieTile.getRowLayout().compareToIgnoreCase("square") == 0 || movieTile.getRowLayout().compareToIgnoreCase("portrait") == 0) {
//                    GlideApp.with(viewHolder.view.getContext())
//                            .load(makeSecure(movieTile.getPortrait()))
//                            .override(dpToPx(viewHolder.view.getContext(), Integer.parseInt(movieTile.getTileWidth())), dpToPx(viewHolder.view.getContext(), Integer.parseInt(movieTile.getTileHeight())))
//                            .into(posterImageView);
//                }
//                ViewGroup.LayoutParams layoutParams = viewHolder.view.getLayoutParams();
//                layoutParams.width = dpToPx(viewHolder.view.getContext(), Integer.parseInt(movieTile.getTileWidth()));
//                layoutParams.height = dpToPx(viewHolder.view.getContext(), Integer.parseInt(movieTile.getTileHeight()));
//                viewHolder.view.setLayoutParams(layoutParams);
//
//            } else {
//                setLayoutOfTile(movieTile, viewHolder.view.getContext(), viewHolder.view, posterImageView);
//            }
//        }
//    }
//
//    @Override
//    public void onUnbindViewHolder(ViewHolder viewHolder) {
//        ImageView posterImageView = viewHolder.view.findViewById(R.id.posterImageView);
//        try {
//            GlideApp.with(viewHolder.view.getContext()).clear(posterImageView);
//        } catch (Exception e) {
//            Timber.e(e);
//        }
//    }
//
//    private void setLayoutOfTile(MovieTile movie, Context context, View view, ImageView imageView) {
//        if (movie != null && movie.getRowLayout() != null) {
//            switch (movie.getRowLayout()) {
//                case "portrait": {
//                    if (movie.getTitle().equals("Refresh")) {
//                        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
//                        imageView.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_refresh_black_24dp));
//                    } else {
//                        int width = dpToPx(context, context.getResources().getInteger(R.integer.tilePotraitWidth));
//                        int height = dpToPx(context, context.getResources().getInteger(R.integer.tilePotraitHeight));
//                        GlideApp.with(context)
//                                .load(makeSecure(movie.getPortrait()))
//                                .override(width, height)
//                                .into(imageView);
//                    }
//                    ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
//                    layoutParams.width = dpToPx(context, context.getResources().getInteger(R.integer.tilePotraitWidth));
//                    layoutParams.height = dpToPx(context, context.getResources().getInteger(R.integer.tilePotraitHeight));
//                    view.setLayoutParams(layoutParams);
//                }
//                break;
//
//                case "square": {
//                    if (movie.getTitle().equals("Refresh")) {
//                        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
//                        imageView.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_refresh_black_24dp));
//                    } else {
//                        int width = dpToPx(context, context.getResources().getInteger(R.integer.tileSquareWidth));
//                        int height = dpToPx(context, context.getResources().getInteger(R.integer.tileSquareHeight));
//                        GlideApp.with(context)
//                                .load(makeSecure(movie.getPortrait()))
//                                .override(width, height)
//                                .into(imageView);
//                    }
//                    ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
//                    layoutParams.width = dpToPx(context, context.getResources().getInteger(R.integer.tileSquareWidth));
//                    layoutParams.height = dpToPx(context, context.getResources().getInteger(R.integer.tileSquareHeight));
//                    view.setLayoutParams(layoutParams);
//                }
//                break;
//
//                case "landscape": {
//                    if (movie.getTitle().equals("Refresh")) {
//                        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
//                        imageView.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_refresh_black_24dp));
//                    } else {
//                        int width = dpToPx(context, context.getResources().getInteger(R.integer.tileLandScapeWidth));
//                        int height = dpToPx(context, context.getResources().getInteger(R.integer.tileLandScapeHeight));
//                        GlideApp.with(context)
//                                .load(makeSecure(movie.getPoster()))
//                                .override(width, height)
//                                .into(imageView);
//                    }
//                    ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
//                    layoutParams.width = dpToPx(context, context.getResources().getInteger(R.integer.tileLandScapeWidth));
//                    layoutParams.height = dpToPx(context, context.getResources().getInteger(R.integer.tileLandScapeHeight));
//                    view.setLayoutParams(layoutParams);
//                }
//                break;
//            }
//        } else {
//            assert movie != null;
//            if (movie.getTitle().equals("Refresh")) {
//                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
//                imageView.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_refresh_black_24dp));
//            } else {
//                GlideApp.with(context)
//                        .load(makeSecure(movie.getPoster()))
//                        .into(imageView);
//            }
//            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
//            layoutParams.width = dpToPx(context, context.getResources().getInteger(R.integer.defaulttileWidth));
//            layoutParams.height = dpToPx(context, context.getResources().getInteger(R.integer.deafulttileHeight));
//            view.setLayoutParams(layoutParams);
//        }
//    }
//    private int dpToPx(Context ctx, int dp) {
//        float density = ctx.getResources()
//                .getDisplayMetrics()
//                .density;
//        return Math.round((float) dp * density);
//    }
//}


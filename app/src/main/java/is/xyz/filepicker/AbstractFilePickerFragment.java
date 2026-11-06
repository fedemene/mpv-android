/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package is.xyz.filepicker;

import is.xyz.mpv.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.util.HashMap;
import java.util.List;

/**
 * A fragment representing a list of Files.
 * <p/>
 * <p/>
 * Activities containing this fragment MUST implement the {@link
 * OnFilePickedListener}
 * interface.
 */
public abstract class AbstractFilePickerFragment<T> extends Fragment
        implements LoaderManager.LoaderCallbacks<List<T>>,
        LogicHandler<T> {

    protected T mCurrentPath = null;
    protected OnFilePickedListener mListener;
    protected FileItemAdapter<T> mAdapter = null;
    protected RecyclerView recyclerView;
    protected LinearLayoutManager layoutManager;
    protected List<T> mFiles = null;
    // Keep track if we are currently loading a directory, in case it takes a long time
    protected boolean isLoading = false;

    private HashMap<String, Integer> mPositionMap;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public AbstractFilePickerFragment() {
        // Retain this fragment across configuration changes, to allow
        // asynctasks and such to be used with ease.
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View view = inflateRootView(inflater, container);

        mPositionMap = new HashMap<>();

        recyclerView = (RecyclerView) view.findViewById(android.R.id.list);
        // improve performance if you know that changes in content
        // do not change the size of the RecyclerView
        //noinspection InvalidSetHasFixedSize
        recyclerView.setHasFixedSize(true);
        // use a linear layout manager
        layoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);
        // Set Item Decoration if exists
        configureItemDecoration(inflater, recyclerView);
        // Set adapter
        mAdapter = new FileItemAdapter<>(this);
        recyclerView.setAdapter(mAdapter);

        onChangePath(mCurrentPath);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    protected View inflateRootView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate( R.layout.nnf_fragment_filepicker, container, false);
    }

    /**
     * Checks if a divider drawable has been defined in the current theme. If it has, will apply
     * an item decoration with the divider. If no divider has been specified, then does nothing.
     */
    protected void configureItemDecoration(@NonNull LayoutInflater inflater,
                                           @NonNull RecyclerView recyclerView) {
        final TypedArray attributes =
                getActivity().obtainStyledAttributes(new int[]{R.attr.nnf_list_item_divider});
        Drawable divider = attributes.getDrawable(0);
        attributes.recycle();

        if (divider != null) {
            recyclerView.addItemDecoration(new DividerItemDecoration(divider));
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            mListener = (OnFilePickedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() +
                    " must implement OnFilePickedListener");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Fall back to root if still null
        if (mCurrentPath == null)
            mCurrentPath = getRoot();
        refresh(mCurrentPath);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    protected void refresh(T nextPath) {
        if (nextPath == null)
            return;
        mCurrentPath = nextPath;
        // Skip loading anything if not initialized yet
        if (getContext() == null)
            return;
        isLoading = true;
        if (hasPermission(nextPath)) {
            LoaderManager.getInstance(this)
                    .restartLoader(0, null, AbstractFilePickerFragment.this);
        } else {
            handlePermission(nextPath);
        }
    }

    protected void handlePermission(@NonNull T path) {
        // Nothing to do by default
    }

    protected boolean hasPermission(@NonNull T path) {
        // Nothing to request by default
        return true;
    }

    @NonNull
    @Override
    public Loader<List<T>> onCreateLoader(final int id, final Bundle args) {
        return getLoader();
    }

    @Override
    public void onLoadFinished(@NonNull final Loader<List<T>> loader,
                               final List<T> data) {
        isLoading = false;
        mFiles = data;
        mAdapter.setList(data);
        onChangePath(mCurrentPath);
        String key = pathToString(mCurrentPath);
        if (mPositionMap.containsKey(key))
            layoutManager.scrollToPositionWithOffset(mPositionMap.get(key), 0);
        else
            layoutManager.scrollToPositionWithOffset(0, 0);
    }

    @Override
    public void onLoaderReset(@NonNull final Loader<List<T>> loader) {
        isLoading = false;
    }

    @Override
    public int getItemViewType(int position, @NonNull T data) {
        if (!isDir(data)) {
            return LogicHandler.VIEWTYPE_FILE;
        } else {
            return LogicHandler.VIEWTYPE_DIR;
        }
    }

    @Override
    public void onBindHeaderViewHolder(@NonNull HeaderViewHolder viewHolder) {
        viewHolder.text.setText("..");
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v;
        switch (viewType) {
            case LogicHandler.VIEWTYPE_HEADER:
                v = LayoutInflater.from(getActivity()).inflate(R.layout.nnf_filepicker_listitem_dir,
                        parent, false);
                return new HeaderViewHolder(v);
            case LogicHandler.VIEWTYPE_FILE:
                v = LayoutInflater.from(getActivity()).inflate(R.layout.nnf_filepicker_listitem_checkable,
                        parent, false);
                return new FileViewHolder(v);
            case LogicHandler.VIEWTYPE_DIR:
            default:
                v = LayoutInflater.from(getActivity()).inflate(R.layout.nnf_filepicker_listitem_dir,
                        parent, false);
                return new DirViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull DirViewHolder vh, int position, @NonNull T data) {
        vh.file = data;
        boolean isDirectory = isDir(data);
        vh.icon.setVisibility(isDirectory ? View.VISIBLE : View.GONE);
        String name = getName(data);
        vh.text.setText(name);

        if (isDirectory || getContext() == null) {
            if (vh.badgeNew != null) vh.badgeNew.setVisibility(View.GONE);
            vh.text.setTextColor(vh.defaultTextColors);
            vh.text.setTypeface(null, Typeface.NORMAL);
            vh.text.setAlpha(1.0f);
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
        int state = prefs.getInt("watched_state_name_" + name, 0);
        int pathState = prefs.getInt("watched_state_path_" + pathToString(data), 0);
        if (pathState > state) {
            state = pathState;
        }

        if (state == 0) {
            // Mai riprodotto: mostra il tag NEW
            if (vh.badgeNew != null) vh.badgeNew.setVisibility(View.VISIBLE);
            vh.text.setTextColor(vh.defaultTextColors);
            vh.text.setTypeface(null, Typeface.NORMAL);
            vh.text.setAlpha(1.0f);
        } else if (state == 1) {
            // Iniziato (< 90%): nascondi tag NEW, testo normale
            if (vh.badgeNew != null) vh.badgeNew.setVisibility(View.GONE);
            vh.text.setTextColor(vh.defaultTextColors);
            vh.text.setTypeface(null, Typeface.NORMAL);
            vh.text.setAlpha(1.0f);
        } else {
            // Riprodotto oltre il 90%: nascondi tag NEW, font grigio e corsivo
            if (vh.badgeNew != null) vh.badgeNew.setVisibility(View.GONE);
            vh.text.setTextColor(Color.GRAY);
            vh.text.setTypeface(null, Typeface.ITALIC);
            vh.text.setAlpha(0.65f);
        }
    }

    public void onClickHeader(@NonNull View view, @NonNull HeaderViewHolder viewHolder) {
        goUp();
    }

    public T getCurrentDir() {
        return mCurrentPath;
    }

    public void goUp() {
        String key = pathToString(mCurrentPath);
        mPositionMap.remove(key);
        goToDir(getParent(mCurrentPath), false);
    }

    public void onClickDir(@NonNull View view, @NonNull DirViewHolder viewHolder) {
        if (isDir(viewHolder.file)) {
            String key = pathToString(mCurrentPath);
            mPositionMap.put(key, layoutManager.findFirstVisibleItemPosition());
            goToDir(viewHolder.file, false);
        }
    }

    public void goToDir(@NonNull T file) {
        goToDir(file, true);
    }

    protected void goToDir(@NonNull T file, boolean force) {
        if (!isLoading || force)
            refresh(file);
    }

    public static int naturalCompare(@NonNull CharSequence a, @NonNull CharSequence b) {
        final int na = a.length(), nb = b.length();
        int ia = 0, ib = 0;
        int zeros = 0;
        int cases = 0;
        while (ia < na && ib < nb) {
            final char ca = a.charAt(ia), cb = b.charAt(ib);
            final boolean da = ca >= '0' && ca <= '9';
            if (ca == cb && !da) {
                ia++;
                ib++;
                continue;
            }
            if (da && cb >= '0' && cb <= '9') {
                int pa = ia, pb = ib;
                while (pa < na && a.charAt(pa) == '0') pa++;
                while (pb < nb && b.charAt(pb) == '0') pb++;
                int bias = 0;
                for (;; pa++, pb++) {
                    final char x = pa < na ? a.charAt(pa) : '\0';
                    final char y = pb < nb ? b.charAt(pb) : '\0';
                    final boolean mx = x >= '0' && x <= '9';
                    final boolean my = y >= '0' && y <= '9';
                    if (!mx || !my) {
                        if (mx) return 1;
                        if (my) return -1;
                        break;
                    }
                    if (bias == 0)
                        bias = x - y;
                }
                if (bias != 0)
                    return bias;
                if (zeros == 0)
                    zeros = (pa - ia) - (pb - ib);
                ia = pa;
                ib = pb;
                continue;
            }
            final char ua = Character.toUpperCase(ca), ub = Character.toUpperCase(cb);
            if (ua != ub) {
                final char la = Character.toLowerCase(ua), lb = Character.toLowerCase(ub);
                if (la != lb)
                    return la - lb;
            }
            if (cases == 0)
                cases = ca - cb;
            ia++;
            ib++;
        }
        if (ia < na) return 1;
        if (ib < nb) return -1;
        return zeros != 0 ? zeros : cases;
    }

    public void onClickCheckable(@NonNull View view, @NonNull FileViewHolder viewHolder) {

    }

    public boolean onLongClickCheckable(@NonNull View view, @NonNull DirViewHolder viewHolder) {
        return false;
    }

    public void onChangePath(T file) {
        // No default implementation
    }

    public interface OnFilePickedListener {
        void onFilePicked(@NonNull File file);
        void onDirPicked(@NonNull File dir);
        void onDocumentPicked(@NonNull Uri uri, boolean isDir);

        void onPermissionGranted();
        void onCancelled();
    }

    public class HeaderViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        final TextView text;

        public HeaderViewHolder(View v) {
            super(v);
            v.setOnClickListener(this);
            text = (TextView) v.findViewById(android.R.id.text1);
        }

        @Override
        public void onClick(View v) {
            onClickHeader(v, this);
        }
    }

    public class DirViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        public final View icon;
        public final TextView text;
        public final TextView badgeNew;
        public final ColorStateList defaultTextColors;
        public T file;

        public DirViewHolder(View v) {
            super(v);
            v.setOnClickListener(this);
            v.setOnLongClickListener(this);
            icon = v.findViewById(R.id.item_icon);
            text = (TextView) v.findViewById(android.R.id.text1);
            badgeNew = (TextView) v.findViewById(R.id.badge_new);
            defaultTextColors = text.getTextColors();
        }

        @Override
        public void onClick(View v) {
            onClickDir(v, this);
        }

        @Override
        public boolean onLongClick(View v) { return onLongClickCheckable(v, this); }
    }

    public class FileViewHolder extends DirViewHolder {

        public FileViewHolder(View v) {
            super(v);
        }

        @Override
        public void onClick(View v) {
            onClickCheckable(v, this);
        }

        @Override
        public boolean onLongClick(View v) { return false; }
    }

}
